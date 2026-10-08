package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.AiAnalysisResponse;
import com.tradenova.report.dto.SessionAiAnalysisRequest;
import com.tradenova.report.dto.SessionAiAnalysisResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAnalysisServiceSessionTest {
    private RestTemplate restTemplate;
    private AiAnalysisService service;
    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        promptBuilder = mock(PromptBuilder.class);
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserPrompt(any())).thenReturn("user");
        when(promptBuilder.buildSessionSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildSessionUserPrompt(any())).thenReturn("user");
        service = new AiAnalysisService(restTemplate, promptBuilder, new ObjectMapper());
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "configured-model");
    }

    @Test
    void parsesLegacyAndStructuredSessionFields() {
        stubResponse("""
                {"score":84,"summary":"grounded","warnings":["w"],"strengths":["s"],
                 "decisionReview":{"assessment":"a","evidence":["episode fact"],"betterAction":"b"},
                 "riskReview":{"assessment":"r","evidence":["plan fact"],"improvement":"i"},
                 "behaviorPatterns":[{"pattern":"p","evidence":["fact"],"impact":"impact","correction":"c"}],
                 "nextTrainingFocus":["focus"]}
                """);

        SessionAiAnalysisResponse result = service.analyzeSession(request());

        assertEquals(0, result.score());
        assertEquals("grounded", result.summary());
        assertEquals(List.of("w"), result.warnings());
        assertEquals(List.of("s"), result.strengths());
        assertEquals("a", result.decisionReview().assessment());
        assertEquals("r", result.riskReview().assessment());
        assertEquals("p", result.behaviorPatterns().get(0).pattern());
        assertEquals(List.of("focus"), result.nextTrainingFocus());
    }

    @Test
    void missingOptionalStructuredArraysBecomeEmptyAndOldFieldsRemainReadable() {
        stubResponse("""
                {"score":70,"summary":"sparse","warnings":[],"strengths":[],
                 "decisionReview":null,"riskReview":null}
                """);

        SessionAiAnalysisResponse result = service.analyzeSession(request());

        assertEquals(0, result.score());
        assertEquals("sparse", result.summary());
        assertEquals(List.of(), result.warnings());
        assertEquals(List.of(), result.strengths());
        assertEquals(List.of(), result.behaviorPatterns());
        assertEquals(List.of(), result.nextTrainingFocus());
        assertNull(result.decisionReview());
        assertNull(result.riskReview());
    }

    @Test
    void malformedStructuredResponseUsesExistingInvalidResponseBehavior() {
        stubResponse("{not-json");

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.analyzeSession(request())
        );

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, exception.getErrorCode());
    }

    @Test
    void sanitizesInternalTokensAcrossStructuredUserFacingFields() {
        stubResponse("""
                {"score":70,"summary":"**stopLossPrice** 없음","warnings":["FOLLOWED"],"strengths":[],
                 "decisionReview":{"assessment":"SNAPSHOT#1","evidence":[],"betterAction":"ACTION-TIME TRADE REASON 확인"},
                 "riskReview":{"assessment":"exitRiskPlan","evidence":["autoExitEnabled"],"improvement":"currentAutoExitEnabled"},
                 "behaviorPatterns":[],"nextTrainingFocus":["takeProfitPrice 기록"]}
                """);

        SessionAiAnalysisResponse result = service.analyzeSession(request());

        assertEquals("**손절가** 없음", result.summary());
        assertEquals("사전에 정한 기준을 따름", result.warnings().get(0));
        assertEquals("사전 계획", result.decisionReview().assessment());
        assertEquals("거래 당시 기록한 이유 확인", result.decisionReview().betterAction());
        assertEquals("청산 시점 리스크 계획", result.riskReview().assessment());
        assertEquals("자동 청산 설정", result.riskReview().evidence().get(0));
        assertEquals("현재 자동 청산 설정", result.riskReview().improvement());
        assertEquals("익절가 기록", result.nextTrainingFocus().get(0));
    }

    @Test
    void sanitizesChartReviewFieldsAndKeepsBoldMarkers() {
        stubResponse("""
                {"score":70,"summary":"**stopLossPrice가 없음**","warnings":["exitRiskPlan FOLLOWED"],
                 "strengths":["currentAutoExitEnabled 확인"]}
                """);

        AiAnalysisResponse result = service.analyze(mock(AiAnalysisRequest.class));

        assertEquals("**손절가가 없음**", result.summary());
        assertEquals("청산 시점 리스크 계획 사전에 정한 기준을 따름", result.warnings().get(0));
        assertEquals("현재 자동 청산 설정 확인", result.strengths().get(0));
    }

    @Test
    void missingChartReviewArraysRemainNullSafeDuringSanitization() {
        stubResponse("""
                {"score":70,"summary":"stopLossPrice 없음"}
                """);

        AiAnalysisResponse result = service.analyze(mock(AiAnalysisRequest.class));

        assertEquals("손절가 없음", result.summary());
        assertEquals(List.of(), result.warnings());
        assertEquals(List.of(), result.strengths());
    }

    private void stubResponse(String content) {
        String envelope = "{\"choices\":[{\"message\":{\"content\":"
                + quote(content) + "}}]}";
        when(restTemplate.exchange(eq("https://api.openai.com/v1/chat/completions"), any(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(envelope));
    }

    private String quote(String value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private SessionAiAnalysisRequest request() {
        return new SessionAiAnalysisRequest(
                1L, 2L, "RANDOM", "COMPLETED", 0, 0, 0, 0, List.of(), List.of());
    }
}
