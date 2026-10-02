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
        assertTrue(prompt.contains("support evidence와 판단을 약화시키는 counter-evidence"));
        assertTrue(prompt.contains("단순 사실 요약은 필요한 경우에만 최소한"));
        assertTrue(prompt.contains("Backend Risk Plan Compliance, FOLLOWED, COMPLETED"));
        assertTrue(prompt.contains("항목당 최대 1~2개 **...**"));
        assertTrue(prompt.contains("** 이외의 Markdown heading, list marker, code block, link 문법은 사용하지 마라"));
        assertTrue(prompt.contains("historicalAutoExitEnabled, currentAutoExitEnabled, actual automatic exit"));
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
        assertTrue(prompt.contains("fact recap보다 그 의미와 trade-off 해석"));
        assertTrue(prompt.contains("support evidence와 약화시킨 counter-evidence"));
        assertTrue(prompt.contains("목표 도달이나 자동 익절 성공을 Decision Quality의 근거로 사용하지 마라"));
        assertTrue(prompt.contains("Backend Risk Plan Compliance, FOLLOWED, COMPLETED"));
        assertTrue(prompt.contains("generic 문구를 사용하지 마라"));
        assertTrue(prompt.contains("항목당 최대 1~2개 **...**"));
        assertTrue(prompt.contains("exitRiskPlan은 청산 거래 시점의 저장된 plan snapshot"));
        assertTrue(prompt.contains("exact linked execution event로만 확인"));
    }
}
