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
        assertTrue(prompt.contains("Never perform arithmetic if the result is supplied by backend facts"));
        assertTrue(prompt.contains("stop과 entry의 대소관계"));
        assertTrue(prompt.contains("structured risk rule과 narrative scenario plan은 서로 다른 evidence"));
        assertTrue(prompt.contains("손절 가격은 설정됐지만 그 근거는 충분히 기록되지 않았다"));
        assertTrue(prompt.contains("When two facts appear inconsistent, explain the scope difference"));
        assertTrue(prompt.contains("stopLossPrice, takeProfitPrice, exitRiskPlan, autoExitEnabled"));
        assertTrue(prompt.contains("이번 거래의 핵심 복기 포인트는 **...**입니다"));
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
        assertTrue(prompt.contains("Never perform arithmetic if the result is supplied by backend facts"));
        assertTrue(prompt.contains("structured risk rule과 narrative scenario plan을 반드시 분리"));
        assertTrue(prompt.contains("가격 기준은 설정됐지만 그 이유가 충분히 설명되지 않았다"));
        assertTrue(prompt.contains("When two facts appear inconsistent, explain the scope difference"));
        assertTrue(prompt.contains("이번 세션에서 가장 중요한 decision-quality observation 하나"));
        assertTrue(prompt.contains("실제로 수행할 수 있는 기록 또는 확인 행동"));
    }
}
