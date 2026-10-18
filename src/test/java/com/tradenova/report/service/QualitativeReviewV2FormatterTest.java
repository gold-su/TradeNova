package com.tradenova.report.service;

import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.ChartLifecycleAiEvidence;
import com.tradenova.report.dto.ChartQualitativeEvidenceContext;
import com.tradenova.report.dto.ScenarioPlanAiEvidence;
import com.tradenova.report.dto.SessionQualitativeEvidenceContext;
import com.tradenova.report.dto.SnapshotAiEvidence;
import com.tradenova.report.dto.TradeActionAiEvidence;
import com.tradenova.report.dto.TradeReasonAiEvidence;
import com.tradenova.training.analytics.DecisionTechnicalContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualitativeReviewV2FormatterTest {

    private final ChartQualitativeReviewV2Formatter formatter = new ChartQualitativeReviewV2Formatter();

    @Test
    void preservesHigherLowAndPriorHighClaimsWithoutMutatingFacts() {
        AiAnalysisRequest request = request(
                "전저점보다 높은 저점이 만들어짐", "", "",
                action("BUY", "전저점보다 높은 저점이 만들어짐", null),
                action("SELL", "전고점까지 왔음; 큰 상승 나와서 일부 매도", null),
                lifecycle(null), false
        );

        String formatted = formatter.format(request);

        assertTrue(formatted.contains("전저점보다 높은 저점이 만들어짐"));
        assertTrue(formatted.contains("전고점까지 왔음"));
        assertTrue(formatted.contains("큰 상승 나와서 일부 매도"));
        assertFalse(formatted.contains("전고점 돌파"));
        assertTrue(formatted.contains("관찰은 기록됐지만 행동 조건이나 무효화 기준으로 고정되지 않음"));
        assertTrue(formatted.contains("무효화 조건이 사전에 고정되지 않음"));
        assertTrue(formatted.contains("no exact stored plan relation"));
        assertTrue(formatted.contains("판단이 틀렸다고 볼 가격 또는 구조"));
        assertTrue(formatted.contains("재량적 반응으로 기록됨"));
        assertFalse(formatted.contains("execution is a strength"));
        assertTrue(AiReviewScoreCalculator.score(request).finalScore() < 80);
    }

    @Test
    void separatesVagueSupportClaimFromSystemMomentumFacts() {
        AiAnalysisRequest request = request(
                "지지받는 느낌", "", "",
                action("BUY", "지지받는 느낌", null),
                action("SELL", "너무 많이 올라서 수익화", null),
                lifecycle(null), false
        );

        String formatted = formatter.format(request);

        assertTrue(formatted.contains("사용자 표현은 있으나 검증할 가격대·조건·구조가 없음"));
        assertTrue(formatted.contains("close relative to SMA20: above"));
        assertTrue(formatted.contains("close relative to SMA60: above"));
        assertTrue(formatted.contains("RSI14: 72.0"));
        assertTrue(formatted.contains("system observations, not claims authored by the user"));
        assertTrue(formatted.contains("재량적 반응으로 기록됨"));
    }

    @Test
    void recognizesStrongLinkedPlanWithoutForcingACaution() {
        ScenarioPlanAiEvidence plan = plan();
        AiAnalysisRequest request = request(
                plan.entryReason(), plan.exitPlan(), plan.riskNote(),
                actionWithQty(1L, "BUY", plan.entryReason(), BigDecimal.ONE, plan),
                actionWithQty(2L, "SELL", plan.exitPlan(), new BigDecimal("0.5"), plan),
                lifecycle(null), true, new BigDecimal("0.5")
        );

        String formatted = formatter.format(request);

        assertTrue(formatted.contains("검증 가능한 조건 또는 가격 기준이 포함됨"));
        assertTrue(formatted.contains("exact stored relation confirms this plan was selected"));
        assertTrue(formatted.contains("no deterministic gap candidate; do not force caution"));
        assertTrue(AiReviewScoreCalculator.score(request).finalScore() >= 80);
    }

    @Test
    void treatsExplicitLinkedWaitAsPotentialExecutionDiscipline() {
        TradeActionAiEvidence wait = action("WAIT", "거래량 증가 조건이 충족되지 않아 관망", plan());
        ChartQualitativeEvidenceContext chart = new ChartQualitativeEvidenceContext(
                1L, true, false, List.of(), List.of(), List.of(wait));

        String formatted = new SessionQualitativeEvidenceFormatter().format(
                new SessionQualitativeEvidenceContext(1L, List.of(chart)));

        assertTrue(formatted.contains("behavior: WAIT"));
        assertTrue(formatted.contains("explicitly linked WAIT may show execution discipline"));
        assertTrue(formatted.contains("no trade is not a weakness"));
    }

    @Test
    void excludesTerminalLiquidationFromDiscretionarySellInterpretation() {
        AiAnalysisRequest request = request(
                "60일선 위 유지 시 진입", "", "",
                action("BUY", "60일선 위 유지 확인", null),
                action("SELL", null, null),
                lifecycle("END_OF_CHART"), false
        );

        String formatted = formatter.format(request);

        assertTrue(formatted.contains("lifecycle-forced liquidation; do not assess as discretionary SELL"));
        assertFalse(formatted.contains("재량적 반응으로 기록됨"));
    }

    @Test
    void sessionSignalsRequireTwoIndependentObservationsBeforeCallingAPattern() {
        ChartQualitativeEvidenceContext first = new ChartQualitativeEvidenceContext(
                1L, true, false, List.of(), List.of(),
                List.of(action("BUY", "지지받는 느낌", null), action("SELL", "많이 올라서", null)));
        ChartQualitativeEvidenceContext second = new ChartQualitativeEvidenceContext(
                2L, true, false, List.of(), List.of(),
                List.of(action("BUY", "오를 것 같아서", null), action("SELL", "너무 떨어져서", null)));

        String multiple = new SessionQualitativeEvidenceFormatter().format(
                new SessionQualitativeEvidenceContext(1L, List.of(first, second)));
        String single = new SessionQualitativeEvidenceFormatter().format(
                new SessionQualitativeEvidenceContext(1L, List.of(first)));

        assertTrue(multiple.contains("analyzed units: 2"));
        assertTrue(multiple.contains("reactive discretionary exit candidates: 2"));
        assertTrue(multiple.contains("at least two independent actions or units"));
        assertTrue(single.contains("analyzed units: 1"));
        assertTrue(single.contains("For one unit, describe one prominent behavior characteristic instead"));
        assertFalse(multiple.contains("tradeId="));
        assertFalse(multiple.contains("chart reference="));
    }

    @Test
    void conditionalRiskPlanIsPrecommitmentAndSizeDeviationBecomesTheDrill() {
        ScenarioPlanAiEvidence plan = new ScenarioPlanAiEvidence(
                55L, 1L, 1, Instant.EPOCH, "20일선 지지", "20일선 지지 시 진입",
                "20일선 이탈 시 50% 매도; 전저점 이탈 시 전량 매도", null, "");
        TradeActionAiEvidence buy = actionWithQty(1L, "BUY", "20일선 지지 확인", new BigDecimal("100"), plan);
        TradeActionAiEvidence sell = actionWithQty(2L, "SELL", "20일선 이탈 조건에서 일부 매도", new BigDecimal("75"), plan);
        AiAnalysisRequest request = request(plan.entryReason(), plan.exitPlan(), null, buy, sell,
                lifecycle(null), false, new BigDecimal("25"));

        String formatted = formatter.format(request);

        assertTrue(formatted.contains("verifiable structural or conditional exit rule"));
        assertTrue(formatted.contains("planned 50%, observed 75%"));
        assertTrue(formatted.contains("position size differs"));
        assertTrue(formatted.contains("사전에 정한 청산 조건과 비중을 실제 행동에 맞춰 보세요"));
        assertFalse(formatted.contains("무효화 조건이 사전에 고정되지 않음"));
        assertFalse(formatted.contains("절대 손절 가격"));
        assertFalse(formatted.contains("자동 청산 미설정"));
    }

    @Test
    void completedBuyAndSellWithoutPlanLinkNeverBecomePlanAdherence() {
        AiAnalysisRequest request = request(
                "20일선 지지", "", "",
                action("BUY", null, null), action("SELL", null, null),
                lifecycle(null), false);

        String formatted = formatter.format(request);

        assertTrue(formatted.contains("Transaction completion alone never establishes plan adherence"));
        assertTrue(formatted.contains("plan/action relationship cannot be confirmed"));
        assertFalse(formatted.contains("plan/action relationship: matches"));
        assertEquals(0, AiReviewScoreCalculator.score(request).execution());
    }

    @Test
    void postHocNumericSellReasonDoesNotBecomePrecommitment() {
        AiAnalysisRequest request = request(
                "20일선 지지", "3.5% 올라서 매도", "",
                action("BUY", "20일선 지지", null),
                action("SELL", "3.5% 올라서 매도", null),
                lifecycle(null), false);

        String formatted = formatter.format(request);

        assertTrue(formatted.contains("action-time explanation specificity"));
        assertTrue(formatted.contains("specificity at action time does not make this a pre-action commitment"));
        assertTrue(formatted.contains("exact action-linked narrative plan: not confirmed"));
        assertTrue(formatted.contains("no pre-action invalidation or exit condition was recorded"));
        assertEquals(0, AiReviewScoreCalculator.score(request).planAndRisk());
    }

    @Test
    void genericSnapshotContextIsNotCountedAsPrecommitmentWithoutExactLink() {
        SnapshotAiEvidence generic = new SnapshotAiEvidence(
                99L, 2, Instant.ofEpochSecond(20), null, "사후 복기", "20일선 지지",
                "20일선 이탈 시 50% 매도", "전저점 이탈 시 무효", "", null);
        ChartQualitativeEvidenceContext chart = new ChartQualitativeEvidenceContext(
                1L, true, false, List.of(generic), List.of(),
                List.of(action("BUY", "20일선 지지", null)));

        String formatted = new SessionQualitativeEvidenceFormatter().format(
                new SessionQualitativeEvidenceContext(1L, List.of(chart)));

        assertTrue(formatted.contains("timing and action attribution are unresolved"));
        assertTrue(formatted.contains("do not score or describe this as precommitment"));
        assertTrue(formatted.contains("plans without an invalidation rationale: 0"));
        assertTrue(formatted.contains("PLAN_ACTION_LINKAGE"));
    }

    private AiAnalysisRequest request(String entry, String exit, String risk,
                                      TradeActionAiEvidence entryAction,
                                      TradeActionAiEvidence latestAction,
                                      ChartLifecycleAiEvidence lifecycle,
                                      boolean structuredRisk) {
        return request(entry, exit, risk, entryAction, latestAction, lifecycle, structuredRisk, BigDecimal.ZERO);
    }

    private AiAnalysisRequest request(String entry, String exit, String risk,
                                      TradeActionAiEvidence entryAction,
                                      TradeActionAiEvidence latestAction,
                                      ChartLifecycleAiEvidence lifecycle,
                                      boolean structuredRisk,
                                      BigDecimal positionQty) {
        return new AiAnalysisRequest(
                "", entry, exit, risk, "", "DEEP", true,
                BigDecimal.valueOf(100), BigDecimal.ONE, BigDecimal.ZERO, positionQty,
                BigDecimal.valueOf(1_000_000),
                structuredRisk ? BigDecimal.valueOf(95) : null,
                structuredRisk ? BigDecimal.valueOf(120) : null,
                structuredRisk, List.of(), List.of(), technical(), technical(), List.of(), List.of(),
                entryAction, latestAction, lifecycle
        );
    }

    private TradeActionAiEvidence action(String side, String reason, ScenarioPlanAiEvidence plan) {
        List<TradeReasonAiEvidence> reasons = reason == null ? List.of()
                : List.of(new TradeReasonAiEvidence("reason", reason, null, Instant.EPOCH));
        return new TradeActionAiEvidence(
                1L, 1L, 1L, side, 1L, BigDecimal.ONE, BigDecimal.valueOf(100),
                Instant.EPOCH, reasons, null, plan == null ? "MANUAL" : "SCENARIO",
                plan == null ? null : plan.snapshotId(), plan
        );
    }

    private TradeActionAiEvidence actionWithQty(Long tradeId, String side, String reason,
                                                 BigDecimal qty, ScenarioPlanAiEvidence plan) {
        List<TradeReasonAiEvidence> reasons = reason == null ? List.of()
                : List.of(new TradeReasonAiEvidence("reason", reason, null, Instant.EPOCH));
        return new TradeActionAiEvidence(
                tradeId, 1L, tradeId, side, tradeId, qty, BigDecimal.valueOf(100),
                Instant.EPOCH, reasons, null, "SCENARIO", plan.snapshotId(), plan
        );
    }

    private ScenarioPlanAiEvidence plan() {
        return new ScenarioPlanAiEvidence(
                1L, 1L, 1, Instant.EPOCH, "추세 지속",
                "60일선 위 종가 유지와 최근 고점 돌파, 거래량 증가 시 진입",
                "이전 고점 접근과 거래량 둔화 시 일부 청산",
                "최근 저점 46400원 종가 이탈 시 손절", ""
        );
    }

    private ChartLifecycleAiEvidence lifecycle(String terminalReason) {
        return new ChartLifecycleAiEvidence(
                "COMPLETED", true, 1L, 2L, "SELL", true,
                terminalReason, terminalReason == null ? null : 2L,
                null, null, false
        );
    }

    private DecisionTechnicalContext technical() {
        return new DecisionTechnicalContext(
                1L, 22, 1_000L, 23, 105.0,
                2.0, 4.0, 101.0, 98.0, 3.96, 7.14,
                72.0, 3.0, 2.86, 900.0, 1_000.0, 0.9,
                108.0, 92.0, -2.78, 14.13,
                115.0, 80.0, -8.70, 31.25,
                List.of(), List.of()
        );
    }
}
