package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.common.exception.CustomException;
import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.AiAnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(req);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

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

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
            }

            return parseResponse(response.getBody());

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
        }
    }

    /**
     * 시스템 프롬프트
     *
     * AI 역할을 고정한다.
     * - 트레이딩 조언을 해주는 게 아니라
     * - 사용자의 "매매 판단 과정"을 평가하는 코치 역할
     */
    private String buildSystemPrompt() {
        return """
                너는 트레이딩 훈련 플랫폼의 AI 코치다.
                너의 역할은 사용자의 매매 판단 과정을 평가하는 것이다.
                
                반드시 JSON 객체만 반환해라.
                아래 형식을 정확히 지켜라.
                
                {
                  "score": 0,
                  "summary": "문장",
                  "warnings": ["문장1", "문장2"],
                  "strengths": ["문장1", "문장2"]
                }
                
                규칙:
                - score는 0~100 사이 정수
                - summary는 1~3문장
                - warnings는 없으면 빈 배열
                - strengths는 없으면 빈 배열
                - 투자 추천/매수 추천 금지
                - 사용자의 리스크 관리, 진입 근거, 감정 통제, 계획 구체성을 평가해라
                """;
    }

    /**
     * 유저 프롬프트 생성
     *
     * AI가 해석하기 쉽게 구조화된 텍스트로 만든다.
     */
    private String buildUserPrompt(AiAnalysisRequest req) {
        return """
                [트레이딩 리포트]
                thesis: %s
                entryReason: %s
                exitPlan: %s
                riskNote: %s
                freeNote: %s
                
                [체결 정보]
                price: %s
                qty: %s
                avgPrice: %s
                positionQty: %s
                
                [최근 종가]
                %s
                
                [최근 거래량]
                %s
                
                위 데이터를 보고:
                1) 판단의 구체성
                2) 리스크 관리 수준
                3) 감정적/충동적 진입 여부
                4) 강점과 나쁜 습관 가능성
                을 평가해라.
                """.formatted(
                nullSafe(req.thesis()),
                nullSafe(req.entryReason()),
                nullSafe(req.exitPlan()),
                nullSafe(req.riskNote()),
                nullSafe(req.freeNote()),
                req.price(),
                req.qty(),
                req.avgPrice(),
                req.positionQty(),
                req.closes(),
                req.volumes()
        );
    }

    /**
     * OpenAI 응답 파싱
     *
     * OpenAI 응답 구조:
     * choices[0].message.content 안에 JSON 문자열이 들어있다.
     */
    private AiAnalysisResponse parseResponse(String rawBody) throws Exception {
        JsonNode root = objectMapper.readTree(rawBody);
        JsonNode contentNode = root.path("choices").get(0).path("message").path("content");

        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
        }

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
