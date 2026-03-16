package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.AiAnalysisResponse;
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
     * null 방어용 헬퍼
     */
    private String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
