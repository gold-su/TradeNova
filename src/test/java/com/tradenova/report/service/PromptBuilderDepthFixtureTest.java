package com.tradenova.report.service;

import com.tradenova.report.dto.*;
import com.tradenova.training.analytics.DecisionTechnicalContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

        assertTrue(prompt.contains("entryReason: 이동평균선 돌파"));
        assertTrue(prompt.contains("USER-AUTHORED ACTION REASON: unavailable"));
        assertTrue(prompt.contains("do not infer causality from a generic snapshot"));
        assertTrue(prompt.contains("reason:너무 떨어져서 매도"));
        assertTrue(prompt.contains("DECISION-TIME OBSERVATIONS ONLY"));
        assertTrue(prompt.contains("SMA20=101.0, SMA60=98.0"));
        assertTrue(prompt.contains("RSI(14)=61.7"));
        assertTrue(prompt.contains("system observations, not proof of what the user checked"));
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

        assertTrue(prompt.contains("EXACT PLAN LINK: linked before the action"));
        assertTrue(prompt.contains("The link proves plan selection, not that every condition was observed"));
        assertTrue(prompt.contains("USER-AUTHORED ACTION REASON"));
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

        assertTrue(formatted.contains("a pre-trade plan, an action-time user reason, an observed fact"));
        assertTrue(formatted.contains("EXACT LINKED PRE-TRADE PLAN"));
        assertTrue(formatted.contains("EXACT PLAN LINK unavailable"));
        assertFalse(formatted.contains("TradeReasonAiEvidence"));
        assertFalse(formatted.contains("tradeId="));
        assertFalse(formatted.contains("riskHistoryId="));
        assertFalse(formatted.contains("event#"));
    }

    private AiAnalysisRequest request(String entryReason, String exitPlan, String riskNote,
                                      TradeActionAiEvidence entryAction,
                                      TradeActionAiEvidence latestAction,
                                      boolean structuredRisk) {
        return new AiAnalysisRequest(
                "", entryReason, exitPlan, riskNote, "", "DEEP", true,
                BigDecimal.valueOf(100), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1_000_000),
                structuredRisk ? BigDecimal.valueOf(95) : null,
                structuredRisk ? BigDecimal.valueOf(120) : null,
                structuredRisk, List.of(), List.of(),
                technicalContext(), technicalContext(), List.of(), List.of(),
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
        return new DecisionTechnicalContext(
                10L, 22, 1_000L, 23, 105.0,
                2.0, 4.0, 101.0, 98.0, 3.96, 7.14,
                61.7, 3.0, 2.86, 1_100.0, 1_000.0, 1.1,
                108.0, 92.0, -2.78, 14.13,
                115.0, 80.0, -8.70, 31.25,
                List.of(), List.of()
        );
    }
}
