package com.tradenova.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.AiAnalysisResponse;
import com.tradenova.report.dto.AiReviewLanguage;
import com.tradenova.report.dto.SessionAiAnalysisRequest;
import com.tradenova.report.dto.SessionAiAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

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
@Slf4j
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
        return analyze(req, null);
    }

    public AiAnalysisResponse analyze(AiAnalysisRequest req, AiReviewLanguage language) {
        // AI 역할 설명서
        String systemPrompt = language == null
                ? promptBuilder.buildSystemPrompt()
                : promptBuilder.buildSystemPrompt(language);

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
        String responseBody = callOpenAi(entity);

        // 응답 JSON -> AiAnalysisResponse 변환
        AiAnalysisResponse parsed = parseChartResponse(
                responseBody,
                language == null ? AiReviewLanguage.KO : language
        );
        int backendScore = AiReviewScoreCalculator.score(req).finalScore();
        return new AiAnalysisResponse(backendScore, parsed.summary(), parsed.warnings(), parsed.strengths());
    }

    private AiAnalysisResponse parseChartResponse(String responseBody, AiReviewLanguage language) {
        try {
            return parseResponse(responseBody, language);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("OpenAI chart response was invalid: exception={}", exception.getClass().getSimpleName());
            throw new CustomException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    /**
     * OpenAI 응답 파싱
     *
     * OpenAI 응답 구조:
     * choices[0].message.content 안에 JSON 문자열이 들어있다.
     */
    private AiAnalysisResponse parseResponse(
            String rawBody,
            AiReviewLanguage language
    ) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(rawBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new CustomException(ErrorCode.AI_RESPONSE_INVALID);
        }
        JsonNode contentNode = choices.path(0).path("message").path("content");

        // content가 비어있으면 실패 처리
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
        }

        // content 문자열을 다시 JSON으로 파싱
        JsonNode aiJson = objectMapper.readTree(contentNode.asText());

        Integer score = aiJson.path("score").asInt(0);
        String summary = AiReviewOutputSanitizer.sanitize(aiJson.path("summary").asText(""), language);

        List<String> warnings = objectMapper.convertValue(
                aiJson.path("warnings"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );

        List<String> strengths = objectMapper.convertValue(
                aiJson.path("strengths"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );

        return new AiAnalysisResponse(
                score,
                summary,
                sanitizeTextList(warnings, language),
                sanitizeTextList(strengths, language)
        );
    }


    /**
     * 세션 전체 데이터를 기반으로 OpenAI API를 호출해
     * 세션 단위 AI 분석 결과를 생성
     */
    public SessionAiAnalysisResponse analyzeSession(SessionAiAnalysisRequest req) {
        return analyzeSession(req, null);
    }

    public SessionAiAnalysisResponse analyzeSession(SessionAiAnalysisRequest req, AiReviewLanguage language) {
        // 1. AI 역할, 출력 형식, 평가 규칙을 담은 system prompt 생성
        String systemPrompt = language == null
                ? promptBuilder.buildSessionSystemPrompt()
                : promptBuilder.buildSessionSystemPrompt(language);
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
        String responseBody = callOpenAi(entity);
        // 8. OpenAI 응답 JSON에서 실제 분석 결과(score, summary 등)를 파싱하여 반환
        SessionAiAnalysisResponse parsed = parseSessionResponseSafely(
                responseBody,
                language == null ? AiReviewLanguage.KO : language
        );
        int backendScore = AiReviewScoreCalculator.score(req).finalScore();
        return new SessionAiAnalysisResponse(
                backendScore, parsed.summary(), parsed.warnings(), parsed.strengths(),
                parsed.decisionReview(), parsed.riskReview(), parsed.behaviorPatterns(),
                firstFocusOnly(parsed.nextTrainingFocus())
        );
    }

    private SessionAiAnalysisResponse parseSessionResponseSafely(
            String responseBody,
            AiReviewLanguage language
    ) {
        try {
            return parseSessionResponse(responseBody, language);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("OpenAI session response was invalid: exception={}", exception.getClass().getSimpleName());
            throw new CustomException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private String callOpenAi(HttpEntity<Map<String, Object>> entity) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.openai.com/v1/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
            }
            return response.getBody();
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                log.warn("OpenAI request timed out: exception={}", rootCauseType(exception));
                throw new CustomException(ErrorCode.AI_API_TIMEOUT);
            }
            log.warn("OpenAI connection failed: exception={}", rootCauseType(exception));
            throw new CustomException(ErrorCode.AI_API_CONNECTION_FAILED);
        } catch (HttpStatusCodeException exception) {
            log.warn("OpenAI API returned an error status: status={}", exception.getStatusCode().value());
            throw new CustomException(ErrorCode.AI_API_CALL_FAILED);
        } catch (RestClientException exception) {
            log.warn("OpenAI HTTP call failed: exception={}", rootCauseType(exception));
            throw new CustomException(ErrorCode.AI_API_CALL_FAILED);
        }
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootCauseType(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private SessionAiAnalysisResponse parseSessionResponse(
            String rawBody,
            AiReviewLanguage language
    ) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(rawBody);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new CustomException(ErrorCode.AI_ANALYSIS_FAILED);
        }
        JsonNode aiJson = objectMapper.readTree(contentNode.asText());
        return objectMapper.treeToValue(
                AiReviewOutputSanitizer.sanitizeTextValues(aiJson, language),
                SessionAiAnalysisResponse.class
        );
    }

    private List<String> sanitizeTextList(List<String> values, AiReviewLanguage language) {
        return values == null
                ? List.of()
                : values.stream().map(value -> AiReviewOutputSanitizer.sanitize(value, language)).toList();
    }

    private List<String> firstFocusOnly(List<String> values) {
        return values == null || values.isEmpty() ? List.of() : List.of(values.get(0));
    }

    /**
     * null 방어용 헬퍼
     */
    private String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
