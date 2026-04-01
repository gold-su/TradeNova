package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.AiAnalysisResponse;
import com.tradenova.report.dto.SessionAiAnalysisRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 연동 전담 서비스
 *
 * 역할
 * - AiAnalysisRequest를 받아 프롬프트를 구성
 * - OpenAI Chat Completions API 호출
 * - 응답 JSON을 AiAnalysisResponse로 변환
 *
 * 설계 원칙
 * - "외부 AI 호출" 책임만 가진다.
 * - DB 조회/저장은 하지 않는다.
 * - ReportAnalysisService가 상위 orchestration 역할을 맡는다.
 */
@Service
@RequiredArgsConstructor
public class AiAnalysisService {
    /**
     * HTTP 호출용 클라이언트
     *
     * 지금은 RestTemplate 사용
     * - 단순하고 빠르게 붙이기 좋음
     * - 나중에 WebClient로 바꿔도 됨
     */
    private final RestTemplate restTemplate;

    /**
     * prompt 주입용
     */
    private final PromptBuilder promptBuilder;

    /**
     * JSON 직렬화/역직렬화
     */
    private final ObjectMapper objectMapper;

    /**
     * OpenAI API Key
     *
     * application.yml 또는 환경변수에서 주입
     * 예:
     * openai:
     *   api-key: xxx
     */
    @Value("${openai.api-key}")
    private String apiKey;

    /**
     * 사용할 모델명
     *
     * 예:
     * gpt-4.1-mini
     * gpt-4o-mini
     *
     * 운영 환경에서 설정값으로 바꾸기 쉽게 분리
     */
    @Value("${openai.model:gpt-4.1-mini}")
    private String model;

    /**
     * AI 분석 실행
     *
     * 입력:
     * - 사용자의 리포트/체결/차트 정보
     *
     * 출력:
     * - 점수, 요약, 경고, 강점
     */
    public AiAnalysisResponse analyze(AiAnalysisRequest req) {
        try {

            // AI 역할 설명서
            String systemPrompt = promptBuilder.buildSystemPrompt();

            // 사용자 리포트/체결 데이터를 문자열로 정리
            String userPrompt = promptBuilder.buildUserPrompt(req);

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // OpenAI 용청 body
            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // OpenAI API 호출
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            System.out.println("=== OpenAI raw response ===");
            System.out.println(response.getBody());
            // 응답이 비정상이면 예외 처리
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
            }

            // 응답 JSON -> AiAnalysisResponse 변환
            return parseResponse(response.getBody());

        }catch (HttpStatusCodeException e){
            System.out.println("=== OpenAI status code ===");
            System.out.println(e.getStatusCode());

            System.out.println("=== OpenAI error body ===");
            System.out.println(e.getResponseBodyAsString());

            throw new CustomException(ErrorCode.AI_API_CALL_FAILED);
        }
        catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    /**
     * OpenAI 응답 파싱
     *
     * OpenAI 응답 구조:
     * choices[0].message.content 안에 JSON 문자열이 들어있다.
     */
    private AiAnalysisResponse parseResponse(String rawBody) throws Exception {
        // rawBody log
        System.out.println("=== parseResponse rawBody ===");
        System.out.println(rawBody);

        JsonNode root = objectMapper.readTree(rawBody);
        // root log
        System.out.println("=== parseResponse root ===");
        System.out.println(root);

        JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
        // contrentNode log
        System.out.println("=== root ===");
        System.out.println(root.toPrettyString());

        // content가 비어있으면 실패 처리
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
        }

        String content = contentNode.asText();
        System.out.println("=== content text ===");
        System.out.println(content);

        // content 문자열을 다시 JSON으로 파싱
        JsonNode aiJson = objectMapper.readTree(contentNode.asText());

        Integer score = aiJson.path("score").asInt(0);
        String summary = aiJson.path("summary").asText("");

        List<String> warnings = objectMapper.convertValue(
                aiJson.path("warnings"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );

        List<String> strengths = objectMapper.convertValue(
                aiJson.path("strengths"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );

        return new AiAnalysisResponse(score, summary, warnings, strengths);
    }


    /**
     * 세션 전체 데이터를 기반으로 OpenAI API를 호출해
     * 세션 단위 AI 분석 결과를 생성
     */
    public AiAnalysisResponse analyzeSession(SessionAiAnalysisRequest req) {
        try {
            // 1. AI 역할, 출력 형식, 평가 규칙을 담은 system prompt 생성
            String systemPrompt = promptBuilder.buildSessionSystemPrompt();
            // 2. 실세 세션 데이터(차트 요약, snapshot 등)를 담은 user prompt 생성
            String userPrompt = promptBuilder.buildSessionUserPrompt(req);

            // 3. OpenAI API 요청 헤더 구성
            //    - JSON 형식으로 요청
            //    - Bearer 토큰으로 인증
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // 4. OpenAI Chat Completions 요청 바디 구성
            //    - model: 사용할 모델명
            //    - temperature: 응답의 랜덤성 (낮을수록 일관적)
            //    - response_format: JSON 객체 형태 강제
            //    - messages: system + user 프롬포트 전달
            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            // 5. 헤더 + 바디를 하나의 HTTP 요청 엔티티로 묶음
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // 6. OpenAI API 호출
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // 7. 응답 상태 코드 또는 body 검증
            //    - 2xx가 아니거나 응답 본문이 없으면 실패 처리
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
            }
            // 8. OpenAI 응답 JSON에서 실제 분석 결과(score, summary 등)를 파싱하여 반환
            return parseResponse(response.getBody());

        } catch (HttpStatusCodeException e) {
            // OpenAI 서버가 4xx / 5xx 응답을 준 경우
            // 예: 인증 실패, 요청 형식 오류, quota 초과 등

            System.out.println("=== OpenAI status code ===");
            System.out.println(e.getStatusCode());

            System.out.println("=== OpenAI error body ===");
            System.out.println(e.getResponseBodyAsString());

            throw new CustomException(ErrorCode.AI_API_CALL_FAILED);
        } catch (CustomException e) {
            // 내부에서 이미 의도적으로 만든 비즈니스 예외는 그대로 전달
            throw e;
        } catch (Exception e) {
            // 그 외 예외는 응답 파싱 실패 / 예상치 못한 런타임 오류로 간주
            throw new CustomException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    /**
     * null 방어용 헬퍼
     */
    private String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
