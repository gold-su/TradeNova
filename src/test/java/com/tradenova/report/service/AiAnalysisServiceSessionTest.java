package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAnalysisServiceSessionTest {
    private RestTemplate restTemplate;
    private AiAnalysisService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        PromptBuilder promptBuilder = mock(PromptBuilder.class);
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

        assertEquals(84, result.score());
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

        assertEquals(70, result.score());
        assertEquals("sparse", result.summary());
        assertEquals(List.of(), result.warnings());
        assertEquals(List.of(), result.strengths());
        assertEquals(List.of(), result.behaviorPatterns());
        assertEquals(List.of(), result.nextTrainingFocus());
        assertNull(result.decisionReview());
        assertNull(result.riskReview());
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
