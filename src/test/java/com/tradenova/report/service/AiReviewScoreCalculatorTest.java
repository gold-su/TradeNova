package com.tradenova.report.service;

import com.tradenova.report.dto.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiReviewScoreCalculatorTest {

    @Test
    void vagueReasonsCannotReachHighScoreEvenWhenAPlanAndExecutionExist() {
        AiReviewScoreCalculator.Score score = AiReviewScoreCalculator.score(chartRequest(
                "오를 것 같아서", "많이 올라서", "떨어지면 손절", true, true, true
        ));

        assertTrue(score.finalScore() < 80);
        assertTrue(score.appliedCap() <= 69);
    }

    @Test
    void groundedButNotFullySpecificProcessStaysBelowNinety() {
        AiReviewScoreCalculator.Score score = AiReviewScoreCalculator.score(chartRequest(
                "60일선 위에서 반등하고 거래량이 증가해 진입",
                "이전 고점 부근에서 매도",
                "최근 저점 이탈 시 손절",
                true, false, true
        ));

        assertTrue(score.finalScore() >= 60, score.toString());
        assertTrue(score.finalScore() < 90);
    }

    @Test
    void productionLikeWeakExitReasonStaysInConservativeRange() {
        SessionAiAnalysisRequest request = sessionRequestWithSeparateExitEvidence(
                "이동평균선 보고 들어가려고 함",
                "지지선 깨지면 손절",
                "많이 올랐음"
        );

        AiReviewScoreCalculator.Score score = AiReviewScoreCalculator.score(request);

        assertTrue(score.finalScore() >= 45, score.toString());
        assertTrue(score.finalScore() <= 59, score.toString());
        assertEquals(1, score.exitSpecificity());
    }

    @Test
    void technicalKeywordListWithoutVerifiableConditionCannotScoreHighly() {
        AiReviewScoreCalculator.Score score = AiReviewScoreCalculator.score(chartRequest(
                "RSI, 이동평균선, 거래량, 지지선, 저항선, 추세를 고려했다",
                "많이 올랐음",
                "지지선 깨지면 손절",
                true, true, true
        ));

        assertEquals(1, score.entrySpecificity());
        assertTrue(score.finalScore() < 80);
    }

    @Test
    void specificVerifiableProcessCanReachEighty() {
        AiReviewScoreCalculator.Score score = AiReviewScoreCalculator.score(chartRequest(
                "171000 부근 저점 반등과 거래량 증가를 확인하고 60일선 위 종가에서 진입",
                "이전 고점 180000 부근에서 거래량 감소가 확인되면 청산",
                "최근 저점 168000 종가 이탈 시 무효화하고 손절",
                true, true, true
        ));

        assertTrue(score.finalScore() >= 80, score.toString());
    }

    @Test
    void missingActionReasonCapsOtherwiseStrongPlan() {
        AiAnalysisRequest request = chartRequest(
                "171000 부근 저점 반등과 거래량 증가를 확인하고 60일선 위 종가에서 진입",
                "이전 고점 180000 부근에서 거래량 감소가 확인되면 청산",
                "최근 저점 168000 종가 이탈 시 무효화하고 손절",
                true, true, false
        );

        AiReviewScoreCalculator.Score score = AiReviewScoreCalculator.score(request);

        assertEquals(79, score.appliedCap());
        assertTrue(score.execution() <= 10);
    }

    @Test
    void followingAVaguePlanOnlyRewardsExecutionAxis() {
        AiReviewScoreCalculator.Score score = AiReviewScoreCalculator.score(sessionRequest(
                "오를 것 같아서", "많이 올라서", "떨어지면 손절", true
        ));

        assertTrue(score.execution() > 0);
        assertTrue(score.finalScore() < 80);
    }

    @Test
    void pnlDoesNotChangeScoreInEitherDirection() {
        SessionAiAnalysisRequest profit = sessionRequest(
                "171000 부근 저점 반등과 거래량 증가를 확인하고 60일선 위 종가에서 진입",
                "이전 고점 180000 부근에서 거래량 감소가 확인되면 청산",
                "최근 저점 168000 종가 이탈 시 무효화하고 손절", false, new BigDecimal("1000000")
        );
        SessionAiAnalysisRequest loss = sessionRequest(
                "171000 부근 저점 반등과 거래량 증가를 확인하고 60일선 위 종가에서 진입",
                "이전 고점 180000 부근에서 거래량 감소가 확인되면 청산",
                "최근 저점 168000 종가 이탈 시 무효화하고 손절", false, new BigDecimal("-1000000")
        );

        assertEquals(AiReviewScoreCalculator.score(profit).finalScore(),
                AiReviewScoreCalculator.score(loss).finalScore());
    }

    @Test
    void singleChartSessionUsesSameEvidenceCalibrationWithoutPatternBonus() {
        AiAnalysisRequest chart = chartRequest(
                "60일선 위에서 반등하고 거래량이 증가해 진입",
                "이전 고점 부근에서 매도",
                "최근 저점 이탈 시 손절", true, true, true
        );
        SessionAiAnalysisRequest session = sessionRequest(
                "60일선 위에서 반등하고 거래량이 증가해 진입",
                "이전 고점 부근에서 매도",
                "최근 저점 이탈 시 손절", false
        );

        int difference = Math.abs(AiReviewScoreCalculator.score(chart).finalScore()
                - AiReviewScoreCalculator.score(session).finalScore());
        assertTrue(difference <= 15);
    }

    @Test
    void conditionalPlanIsNotUndervaluedAgainstAutomaticPricePlan() {
        AiReviewScoreCalculator.Score conditional = AiReviewScoreCalculator.score(chartRequest(
                "20일선 지지 시 진입",
                "20일선 이탈 시 50% 매도; 전저점 이탈 시 전량 매도",
                "전저점 이탈 시 시나리오 무효", false, false, true));
        AiReviewScoreCalculator.Score automated = AiReviewScoreCalculator.score(chartRequest(
                "20일선 지지 시 진입", "", "", true, true, true));

        assertTrue(conditional.planAndRisk() >= automated.planAndRisk());
    }

    @Test
    void transactionCompletionWithoutReasonOrComplianceAddsNoExecutionQuality() {
        AiReviewScoreCalculator.Score score = AiReviewScoreCalculator.score(chartRequest(
                "20일선 지지", "", "", false, false, false));

        assertEquals(0, score.execution());
    }

    private AiAnalysisRequest chartRequest(String entry, String exit, String risk,
                                           boolean structuredRisk, boolean autoExit, boolean actionReasons) {
        TradeActionAiEvidence buy = action(1L, "BUY", actionReasons ? entry : null);
        TradeActionAiEvidence sell = action(2L, "SELL", actionReasons ? exit : null);
        return new AiAnalysisRequest(
                entry, entry, exit, risk, "", "DEEP", true,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                structuredRisk ? BigDecimal.ONE : null,
                structuredRisk ? BigDecimal.valueOf(20) : null,
                autoExit, List.of(), List.of(), null, null, List.of(), List.of(), buy, sell,
                new ChartLifecycleAiEvidence("COMPLETED", true, 1L, 2L, "SELL", true,
                        null, null, null, autoExit, false)
        );
    }

    private TradeActionAiEvidence action(Long id, String side, String reason) {
        List<TradeReasonAiEvidence> reasons = reason == null ? List.of()
                : List.of(new TradeReasonAiEvidence("reason", reason, null, Instant.EPOCH));
        return new TradeActionAiEvidence(id, 10L, id, side, id, BigDecimal.ONE, BigDecimal.TEN,
                Instant.EPOCH, reasons, null, "MANUAL", null, null);
    }

    private SessionAiAnalysisRequest sessionRequest(String entry, String exit, String risk, boolean followed) {
        return sessionRequest(entry, exit, risk, followed, BigDecimal.ZERO);
    }

    private SessionAiAnalysisRequest sessionRequest(String entry, String exit, String risk,
                                                    boolean followed, BigDecimal pnl) {
        ChartQualitativeEvidenceContext qualitativeChart = new ChartQualitativeEvidenceContext(
                10L, true, false, List.of(), List.of(),
                List.of(action(1L, "BUY", entry), action(2L, "SELL", exit))
        );
        RiskComplianceAiEvidence compliance = new RiskComplianceAiEvidence(
                null, 2L, 3L, true, true, true, "STOP_LOSS", 100,
                BigDecimal.valueOf(100), BigDecimal.ONE, BigDecimal.ONE, true,
                followed ? "FOLLOWED" : "UNKNOWN", "fixture"
        );
        return new SessionAiAnalysisRequest(
                1L, 2L, "RANDOM", "COMPLETED", 1, 1, 2, 2,
                List.of(new SessionChartSummary(10L, 0, "T", "Test", "COMPLETED", 10,
                        2, 2, 1, true, pnl)),
                List.of(new SessionSnapshotSummary(10L, 1, entry, entry, exit, risk, "")),
                null,
                new SessionQualitativeEvidenceContext(1L, List.of(qualitativeChart), List.of(compliance))
        );
    }

    private SessionAiAnalysisRequest sessionRequestWithSeparateExitEvidence(
            String entryReason, String exitPlan, String sellReason) {
        ChartQualitativeEvidenceContext qualitativeChart = new ChartQualitativeEvidenceContext(
                10L, true, false, List.of(), List.of(),
                List.of(action(1L, "BUY", entryReason), action(2L, "SELL", sellReason))
        );
        RiskComplianceAiEvidence compliance = new RiskComplianceAiEvidence(
                null, 2L, 3L, true, true, true, "TAKE_PROFIT", 100,
                BigDecimal.valueOf(100), BigDecimal.ONE, BigDecimal.ONE, true,
                "FOLLOWED", "fixture"
        );
        return new SessionAiAnalysisRequest(
                1L, 2L, "RANDOM", "COMPLETED", 1, 1, 2, 2,
                List.of(new SessionChartSummary(10L, 0, "T", "Test", "COMPLETED", 10,
                        2, 2, 1, true, BigDecimal.ZERO)),
                List.of(new SessionSnapshotSummary(10L, 1, entryReason, entryReason, exitPlan, "", "")),
                null,
                new SessionQualitativeEvidenceContext(1L, List.of(qualitativeChart), List.of(compliance))
        );
    }
}
