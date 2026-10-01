package com.tradenova.report.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderReviewQualityTest {

    private final PromptBuilder promptBuilder = new PromptBuilder(
            new SessionAiDeterministicContextFormatter()
    );

    @Test
    void chartReviewPrioritizesDecisionEvidenceWithoutChangingResponseSchema() {
        String prompt = promptBuilder.buildSystemPrompt();

        assertTrue(prompt.contains("\"score\": 0"));
        assertTrue(prompt.contains("\"summary\": \"문장\""));
        assertTrue(prompt.contains("Decision Quality, Risk / Plan Quality, Execution Quality, Learning Point"));
        assertTrue(prompt.contains("entryDecisionTechnicalContext와 entryDecisionOhlcv"));
        assertTrue(prompt.contains("profit != good decision, loss != bad decision"));
        assertTrue(prompt.contains("use only supplied evidence"));
        assertTrue(prompt.contains("동일 사실은 summary, strengths, warnings 전체에서 한 번만"));
        assertTrue(prompt.contains("raw HTML, CSS, 색상 코드를 출력하지 마라"));
    }

    @Test
    void sessionReviewSeparatesReviewAxesAndSuppressesRepeatedPlanPraise() {
        String prompt = promptBuilder.buildSessionSystemPrompt();

        assertTrue(prompt.contains("Decision Quality, Risk / Plan Quality, Execution Quality, Learning Point"));
        assertTrue(prompt.contains("계획 일관성은 하나의 평가 축일 뿐"));
        assertTrue(prompt.contains("summary, strengths, warnings, riskReview, nextTrainingFocus 전체에서 한 번만"));
        assertTrue(prompt.contains("여러 chart/trade에서 반복 확인된 패턴"));
        assertTrue(prompt.contains("use only supplied evidence"));
        assertTrue(prompt.contains("다음 훈련에서 관찰하거나 기록할 구체적인 행동 하나"));
        assertTrue(prompt.contains("raw HTML, CSS, 색상 코드를 출력하지 마라"));
    }
}
