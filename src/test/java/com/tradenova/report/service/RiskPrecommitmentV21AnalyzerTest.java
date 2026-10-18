package com.tradenova.report.service;

import com.tradenova.report.dto.ScenarioPlanAiEvidence;
import com.tradenova.report.dto.TradeActionAiEvidence;
import com.tradenova.report.dto.TradeReasonAiEvidence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskPrecommitmentV21AnalyzerTest {

    @Test
    void recognizesConditionalManualRiskPlanWithoutNumericOrAutomaticRule() {
        var result = RiskPrecommitmentV21Analyzer.classify(
                "20일선 이탈 시 50% 매도; 전저점 이탈 시 전량 매도",
                null, null, null, false);

        assertEquals(RiskPrecommitmentV21Analyzer.PlanType.CONDITIONAL, result.type());
        assertTrue(result.exists());
        assertTrue(result.verifiable());
        assertFalse(result.automatedExecution());
    }

    @Test
    void distinguishesNoPlanVaguePriceAndAutomatedPlans() {
        assertEquals(RiskPrecommitmentV21Analyzer.PlanType.NO_PLAN,
                RiskPrecommitmentV21Analyzer.classify(null, null, null, null, false).type());
        assertEquals(RiskPrecommitmentV21Analyzer.PlanType.VAGUE,
                RiskPrecommitmentV21Analyzer.classify("적당히 오르면 매도", null, null, null, false).type());
        assertEquals(RiskPrecommitmentV21Analyzer.PlanType.PRICE_BASED,
                RiskPrecommitmentV21Analyzer.classify(null, null, BigDecimal.valueOf(95), null, false).type());
        assertEquals(RiskPrecommitmentV21Analyzer.PlanType.AUTOMATED,
                RiskPrecommitmentV21Analyzer.classify(null, null, BigDecimal.valueOf(95), null, true).type());
    }

    @Test
    void detectsSizeDeviationWhenTriggerAndPartialActionMatch() {
        ScenarioPlanAiEvidence plan = plan("20일선 이탈 시 50% 매도");
        TradeActionAiEvidence sell = action("20일선 이탈 조건에서 일부 매도", new BigDecimal("75"), plan);

        var comparison = RiskPrecommitmentV21Analyzer.compare(plan, sell, new BigDecimal("100"));

        assertEquals(RiskPrecommitmentV21Analyzer.Relation.PARTIAL_DEVIATION, comparison.relation());
        assertEquals(RiskPrecommitmentV21Analyzer.DimensionRelation.MATCH, comparison.trigger());
        assertEquals(RiskPrecommitmentV21Analyzer.DimensionRelation.MATCH, comparison.action());
        assertEquals(RiskPrecommitmentV21Analyzer.DimensionRelation.MISMATCH, comparison.size());
        assertEquals(0, new BigDecimal("75").compareTo(comparison.actualPercent()));
    }

    @Test
    void detectsLossVersusGainTriggerDeviationEvenWhenBothActionsAreFullExit() {
        ScenarioPlanAiEvidence plan = plan("-5% 손실 시 전량 매도");
        TradeActionAiEvidence sell = action("+8% 상승 구간에서 전량 매도", new BigDecimal("100"), plan);

        var comparison = RiskPrecommitmentV21Analyzer.compare(plan, sell, new BigDecimal("100"));

        assertEquals(RiskPrecommitmentV21Analyzer.Relation.PARTIAL_DEVIATION, comparison.relation());
        assertEquals(RiskPrecommitmentV21Analyzer.DimensionRelation.MISMATCH, comparison.trigger());
        assertEquals(RiskPrecommitmentV21Analyzer.DimensionRelation.MATCH, comparison.action());
        assertEquals(RiskPrecommitmentV21Analyzer.DimensionRelation.MATCH, comparison.size());
        assertEquals(RiskPrecommitmentV21Analyzer.PlanType.CONDITIONAL,
                RiskPrecommitmentV21Analyzer.classify(plan).type());
    }

    @Test
    void comparesFullExitToFullExitClauseWhenPlanContainsMultipleExitRules() {
        ScenarioPlanAiEvidence plan = plan("20일선 이탈 시 50% 매도; 전저점 이탈 시 전량 매도");
        TradeActionAiEvidence sell = action("4% 올라서 풀매도", new BigDecimal("100"), plan);

        var comparison = RiskPrecommitmentV21Analyzer.compare(plan, sell, new BigDecimal("100"));

        assertEquals(RiskPrecommitmentV21Analyzer.Relation.PARTIAL_DEVIATION, comparison.relation());
        assertEquals(RiskPrecommitmentV21Analyzer.DimensionRelation.MISMATCH, comparison.trigger());
        assertEquals(RiskPrecommitmentV21Analyzer.DimensionRelation.MATCH, comparison.action());
        assertEquals(RiskPrecommitmentV21Analyzer.DimensionRelation.MATCH, comparison.size());
    }

    @Test
    void recognizesActionTimePlanChangeWithRationaleAsAdaptationCandidate() {
        ScenarioPlanAiEvidence plan = plan("전저점 이탈 시 전량 매도");
        TradeActionAiEvidence sell = action(
                "새 정보로 기존 계획을 변경: 거래량 둔화가 확인되어 지금 전량 매도",
                new BigDecimal("100"), plan);

        var comparison = RiskPrecommitmentV21Analyzer.compare(plan, sell, new BigDecimal("100"));

        assertEquals(RiskPrecommitmentV21Analyzer.Relation.ADAPTATION_CANDIDATE, comparison.relation());
        assertTrue(comparison.planChangeRecorded());
    }

    @Test
    void planChangeLabelWithoutRationaleIsNotTreatedAsJustifiedAdaptation() {
        ScenarioPlanAiEvidence plan = plan("전저점 이탈 시 전량 매도");
        TradeActionAiEvidence sell = action("기존 계획 변경 후 전량 매도", new BigDecimal("100"), plan);

        var comparison = RiskPrecommitmentV21Analyzer.compare(plan, sell, new BigDecimal("100"));

        assertFalse(comparison.planChangeRecorded());
        assertEquals(RiskPrecommitmentV21Analyzer.Relation.UNVERIFIABLE, comparison.relation());
    }

    @Test
    void transactionCompletionWithoutExactPlanLinkIsUnverifiable() {
        var comparison = RiskPrecommitmentV21Analyzer.compare(
                null, action("매도 완료", BigDecimal.TEN, null), BigDecimal.TEN);

        assertEquals(RiskPrecommitmentV21Analyzer.Relation.UNVERIFIABLE, comparison.relation());
    }

    private ScenarioPlanAiEvidence plan(String exitPlan) {
        return new ScenarioPlanAiEvidence(55L, 10L, 1, Instant.EPOCH,
                "20일선 지지", "20일선 지지 시 진입", exitPlan, null, null);
    }

    private TradeActionAiEvidence action(String reason, BigDecimal qty, ScenarioPlanAiEvidence plan) {
        return new TradeActionAiEvidence(2L, 10L, 2L, "SELL", 20L, qty, BigDecimal.TEN,
                Instant.EPOCH, List.of(new TradeReasonAiEvidence("청산", reason, null, Instant.EPOCH)),
                null, plan == null ? "MANUAL" : "SCENARIO", plan == null ? null : plan.snapshotId(), plan);
    }
}
