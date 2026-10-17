package com.tradenova.report.service;

import com.tradenova.report.dto.*;
import com.tradenova.training.analytics.DecisionTechnicalContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderDepthFixtureTest {

    private final PromptBuilder promptBuilder = new PromptBuilder(new SessionAiDeterministicContextFormatter());

    @Test
    void weakFixtureKeepsScenarioActionAndTechnicalFactsInSeparateScopes() {
        AiAnalysisRequest request = request(
                "이동평균선 돌파",
                "",
                "",
                null,
                action(2L, "SELL", "너무 떨어져서 매도", null),
                false
        );

        String prompt = promptBuilder.buildUserPrompt(request);

        assertTrue(prompt.contains("pre-trade entry statement: 이동평균선 돌파"));
        assertTrue(prompt.contains("entry action: no canonical action-time reason"));
        assertTrue(prompt.contains("chronology alone does not establish causality"));
        assertTrue(prompt.contains("latest action user reason: 너무 떨어져서 매도"));
        assertTrue(prompt.contains("DECISION-TIME FACTS"));
        assertTrue(prompt.contains("close relative to SMA20: above"));
        assertTrue(prompt.contains("RSI14: 61.7"));
        assertTrue(prompt.contains("Do not imply the user observed them"));
        assertFalse(prompt.contains("DecisionTechnicalContext["));
        assertTrue(AiReviewScoreCalculator.score(request).finalScore() < 80);
    }

    @Test
    void concreteFixturePreservesExactPlanLinkAndCanKeepExistingHighScoreCalibration() {
        ScenarioPlanAiEvidence plan = new ScenarioPlanAiEvidence(
                5L, 10L, 1, Instant.EPOCH,
                "추세 지속 가설",
                "60일선 위 종가 유지와 최근 고점 돌파, 거래량 증가 시 진입",
                "이전 고점 접근과 거래량 둔화 시 일부 청산",
                "최근 저점 46400원 종가 이탈 시 손절",
                ""
        );
        AiAnalysisRequest request = request(
                plan.entryReason(), plan.exitPlan(), plan.riskNote(),
                action(1L, "BUY", plan.entryReason(), plan),
                action(2L, "SELL", plan.exitPlan(), null),
                true
        );

        String prompt = promptBuilder.buildUserPrompt(request);

        assertTrue(prompt.contains("exact stored relation confirms this plan was selected"));
        assertTrue(prompt.contains("does not confirm every condition occurred"));
        assertTrue(prompt.contains("entry action user reason"));
        assertTrue(AiReviewScoreCalculator.score(request).finalScore() >= 80);
    }

    @Test
    void sessionEvidenceFormatterEncouragesCrossActionSynthesisInsteadOfRawRecordRecap() {
        ScenarioPlanAiEvidence plan = new ScenarioPlanAiEvidence(
                5L, 10L, 1, Instant.EPOCH, "추세", "60일선 위 유지 시 진입",
                "이전 고점 접근 시 청산", "최근 저점 이탈 시 손절", ""
        );
        ChartQualitativeEvidenceContext chart = new ChartQualitativeEvidenceContext(
                10L, true, false, List.of(), List.of(),
                List.of(
                        action(1L, "BUY", "60일선 위 유지 확인", plan),
                        action(2L, "SELL", "너무 떨어져서 매도", null)
                )
        );

        String formatted = new SessionQualitativeEvidenceFormatter().format(
                new SessionQualitativeEvidenceContext(1L, List.of(chart))
        );

        assertTrue(formatted.contains("USER CLAIM -> FACT -> GAP -> BEHAVIOR -> IMPLICATION -> NEXT DRILL"));
        assertTrue(formatted.contains("exact stored relation confirms plan selection"));
        assertTrue(formatted.contains("no exact stored relation"));
        assertFalse(formatted.contains("TradeReasonAiEvidence"));
        assertFalse(formatted.contains("tradeId="));
        assertFalse(formatted.contains("riskHistoryId="));
        assertFalse(formatted.contains("event#"));
    }

    @Test
    void actualQaFixtureKeepsWeakClaimQualityWithoutTransactionCompletionBonus() {
        DecisionTechnicalContext overboughtContext = technicalContext(72.0);
        AiAnalysisRequest request = request(
                "매물대 형성 지지",
                "",
                "",
                action(1L, "BUY", "지지받고 있는 느낌", null),
                action(2L, "SELL", "너무 많이 올라서 수익화", null),
                false,
                overboughtContext
        );

        String userPrompt = promptBuilder.buildUserPrompt(request);
        String chartContract = promptBuilder.buildSystemPrompt();
        String sessionContract = promptBuilder.buildSessionSystemPrompt();

        assertTrue(userPrompt.contains("pre-trade entry statement: 매물대 형성 지지"));
        assertTrue(userPrompt.contains("entry action user reason: 지지받고 있는 느낌"));
        assertTrue(userPrompt.contains("latest action user reason: 너무 많이 올라서 수익화"));
        assertTrue(userPrompt.contains("RSI14: 72.0"));
        assertTrue(userPrompt.contains("Do not imply the user observed them"));
        assertTrue(chartContract.contains("명확하거나 구체적인 진입 근거라고 부르지 마라"));
        assertTrue(sessionContract.contains("Chart에서는 모호하다고 하고 Session에서는 명확하다고 뒤집지 마라"));
        assertTrue(sessionContract.contains("상위 행동 특성 하나로 합성"));
        assertEquals(30, AiReviewScoreCalculator.score(request).finalScore());
    }

    private AiAnalysisRequest request(String entryReason, String exitPlan, String riskNote,
                                      TradeActionAiEvidence entryAction,
                                      TradeActionAiEvidence latestAction,
                                      boolean structuredRisk) {
        return request(entryReason, exitPlan, riskNote, entryAction, latestAction, structuredRisk,
                technicalContext());
    }

    private AiAnalysisRequest request(String entryReason, String exitPlan, String riskNote,
                                      TradeActionAiEvidence entryAction,
                                      TradeActionAiEvidence latestAction,
                                      boolean structuredRisk,
                                      DecisionTechnicalContext technicalContext) {
        return new AiAnalysisRequest(
                "", entryReason, exitPlan, riskNote, "", "DEEP", true,
                BigDecimal.valueOf(100), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1_000_000),
                structuredRisk ? BigDecimal.valueOf(95) : null,
                structuredRisk ? BigDecimal.valueOf(120) : null,
                structuredRisk, List.of(), List.of(),
                technicalContext, technicalContext, List.of(), List.of(),
                entryAction, latestAction,
                new ChartLifecycleAiEvidence(
                        "COMPLETED", true, 1L, 2L, "SELL", true,
                        null, null, structuredRisk ? 7L : null,
                        structuredRisk, false
                )
        );
    }

    private TradeActionAiEvidence action(Long tradeId, String side, String reason, ScenarioPlanAiEvidence plan) {
        List<TradeReasonAiEvidence> reasons = reason == null ? List.of()
                : List.of(new TradeReasonAiEvidence("reason", reason, null, Instant.EPOCH));
        return new TradeActionAiEvidence(
                tradeId, 10L, tradeId, side, tradeId, BigDecimal.ONE, BigDecimal.valueOf(100),
                Instant.EPOCH, reasons, null,
                plan == null ? "MANUAL" : "SCENARIO", plan == null ? null : plan.snapshotId(), plan
        );
    }

    private DecisionTechnicalContext technicalContext() {
        return technicalContext(61.7);
    }

    private DecisionTechnicalContext technicalContext(double rsi) {
        return new DecisionTechnicalContext(
                10L, 22, 1_000L, 23, 105.0,
                2.0, 4.0, 101.0, 98.0, 3.96, 7.14,
                rsi, 3.0, 2.86, 1_100.0, 1_000.0, 1.1,
                108.0, 92.0, -2.78, 14.13,
                115.0, 80.0, -8.70, 31.25,
                List.of(), List.of()
        );
    }
}
