package com.tradenova.report.service;

import com.tradenova.report.dto.ChartAiDeterministicContext;
import com.tradenova.report.dto.RiskPlanAiContext;
import com.tradenova.report.dto.SessionAiAnalysisRequest;
import com.tradenova.report.dto.SessionAiDeterministicContext;
import com.tradenova.report.dto.SessionSnapshotSummary;
import com.tradenova.report.dto.SessionChartSummary;
import com.tradenova.report.dto.TradeEpisodeAiContext;
import com.tradenova.report.dto.ChartQualitativeEvidenceContext;
import com.tradenova.report.dto.EvidenceTimelineAnchor;
import com.tradenova.report.dto.NoteAiEvidence;
import com.tradenova.report.dto.SessionQualitativeEvidenceContext;
import com.tradenova.report.dto.SnapshotAiEvidence;
import com.tradenova.training.analytics.SessionTradeStatistics;
import com.tradenova.training.analytics.TradeEpisodeReference;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAiPromptDeterministicContextTest {

    private final PromptBuilder promptBuilder = new PromptBuilder(
            new SessionAiDeterministicContextFormatter()
    );

    @Test
    void formatsStatisticsInactiveEvidenceSnapshotsAndEventCount() {
        SessionAiAnalysisRequest request = new SessionAiAnalysisRequest(
                1L,
                2L,
                "RANDOM",
                "COMPLETED",
                1,
                1,
                3,
                7,
                List.of(),
                List.of(new SessionSnapshotSummary(
                        10L, 2, "support held", "pullback", "planned exit", "risk note", "memo"
                )),
                contextWithClosedAndOpenEpisodes(),
                qualitativeContext()
        );

        String prompt = promptBuilder.buildSessionUserPrompt(request);

        assertTrue(prompt.contains("[Deterministic Session Facts]"));
        assertTrue(prompt.contains("[BACKEND SCORING RUBRIC - score source of truth]"));
        assertTrue(prompt.contains("backendFinalScore="));
        assertTrue(prompt.contains("decisionEvidence="));
        assertFalse(prompt.contains("activeChartCount"));
        assertTrue(prompt.contains("[Calculated Trade Statistics]"));
        assertTrue(prompt.contains("closedEpisodes=1, openEpisodes=1"));
        assertTrue(prompt.contains("Outcome statistics are intentionally excluded"));
        assertFalse(prompt.contains("P&L"));
        assertFalse(prompt.contains("winRate="));
        assertFalse(prompt.contains("payoffRatio="));
        assertFalse(prompt.contains("expectancy="));
        assertFalse(prompt.contains("totalRealizedPnl="));
        assertFalse(prompt.contains("averageReturnPct="));
        assertTrue(prompt.contains("state=CLOSED"));
        assertTrue(prompt.contains("state=OPEN"));
        assertTrue(prompt.contains("active=false, refreshed=true"));
        assertTrue(prompt.contains("entry-time structured risk={stop configured=true, target configured=true, automatic exit configured=true"));
        assertTrue(prompt.contains("exit-time structured risk={stop configured=true, target configured=true, automatic exit configured=false"));
        assertTrue(prompt.contains("entry-time structured risk=none"));
        assertFalse(prompt.contains("realizedPnl="));
        assertFalse(prompt.contains("weightedEntryPrice="));
        assertFalse(prompt.contains("tradeRefs="));
        assertFalse(prompt.contains("101, 102"));
        assertTrue(prompt.contains("[SESSION QUALITATIVE REVIEW V2 INPUT]"));
        assertTrue(prompt.contains("thesis: support held"));
        assertTrue(prompt.contains("USER NOTE (do not assign it to an earlier action"));
        assertTrue(prompt.contains("[Automatic Event Context"));
        assertTrue(prompt.contains("totalEventCount: 7"));
    }

    @Test
    void systemPromptKeepsResponseSchemaAndAddsEvidenceRules() {
        String prompt = promptBuilder.buildSessionSystemPrompt();

        assertTrue(prompt.contains("\"score\": 0"));
        assertTrue(prompt.contains("\"summary\": \"문장\""));
        assertTrue(prompt.contains("\"warnings\""));
        assertTrue(prompt.contains("\"strengths\""));
        assertTrue(prompt.contains("다시 계산하거나 수정하지 마라"));
        assertTrue(prompt.contains("OPEN episode"));
        assertTrue(prompt.contains("riskRuleHistoryId"));
        assertTrue(prompt.contains("변경 원인이나 사용자의 심리를 추정하지 마라"));
        assertTrue(prompt.contains("시간적으로 뒤에 작성된 NOTE"));
        assertTrue(prompt.contains("자동 TRADE/WARNING/PROGRESS/AI event"));
    }

    @Test
    void systemPromptPreventsOutcomeBiasAndSeparatesRiskQualityFromCompliance() {
        String prompt = promptBuilder.buildSessionSystemPrompt();

        assertTrue(prompt.contains("loss != bad decision"));
        assertTrue(prompt.contains("profit != good decision"));
        assertTrue(prompt.contains("PnL alone must never determine decision quality"));
        assertTrue(prompt.contains("return magnitude alone must never determine risk quality"));
        assertTrue(prompt.contains("Risk Plan Quality와 Risk Plan Compliance를 반드시 분리"));
        assertTrue(prompt.contains("계획된 stop에 따른 손실 청산은 compliance 저하의 근거가 아니다"));
        assertTrue(prompt.contains("계획을 지킨 실행의 compliance는 positive 또는 neutral"));
        assertTrue(prompt.contains("qualitative review input에서 outcome 수치가 제외된 경우 P&L, 수익, 손실을 추측하거나 언급하지 마라"));
        assertTrue(prompt.contains("조건형/구조형 청산 계획은 절대 가격이나 자동 설정이 없어도 precommitment"));
        assertTrue(prompt.contains("numeric stop/target, structural/conditional plan, manual/automatic execution은 서로 독립된 축"));
    }

    @Test
    void systemPromptKeepsNoTradeNeutralAndForbidsDiversificationOrForcedTradingAdvice() {
        String prompt = promptBuilder.buildSessionSystemPrompt();

        assertTrue(prompt.contains("거래하지 않은 차트는 기본적으로 neutral evidence"));
        assertTrue(prompt.contains("no trade를 disciplined risk management, missed opportunity, hesitation, successful avoidance로 자동 해석하지 마라"));
        assertTrue(prompt.contains("분산 부족을 비판하지 마라"));
        assertTrue(prompt.contains("더 많은 종목/차트/기회를 거래하거나 거래 빈도를 높이라고 권고하지 마라"));
        assertTrue(prompt.contains("tradedChartCount와 totalChartCount의 차이는 판단 품질이나 분산 품질의 직접 근거가 아니다"));
        assertTrue(prompt.contains("미거래 이유 부재 자체를 warnings, recommendations, nextTrainingFocus"));
        assertTrue(prompt.contains("미거래 이유, 미거래 계획, 거래 빈도 증가, 종목 선택 확대를 기록하거나 연습하라고 권고하지 마라"));
    }

    @Test
    void omitsUnevaluatedNoTradeChartInventoryFromDeterministicEvidence() {
        ChartAiDeterministicContext noTrade = new ChartAiDeterministicContext(
                99L, 3, 30L, "NONE", "No trade", "ETC", LocalDate.now(), LocalDate.now(),
                10, 9, "COMPLETED", true, false, 0, 0, false, List.of());
        SessionAiDeterministicContext base = contextWithClosedAndOpenEpisodes();
        SessionAiDeterministicContext withNoTrade = new SessionAiDeterministicContext(
                base.sessionId(), base.userId(), base.accountId(), base.mode(), base.sessionStatus(),
                4, base.activeChartCount(), 4, 1, base.totalTradeCount(), base.tradeStatistics(),
                java.util.stream.Stream.concat(base.charts().stream(), java.util.stream.Stream.of(noTrade)).toList());

        String facts = new SessionAiDeterministicContextFormatter().formatSessionFacts(withNoTrade);
        String charts = new SessionAiDeterministicContextFormatter().formatChartEvidence(withNoTrade);
        assertFalse(facts.contains("tradedChartCount"));
        assertFalse(facts.contains("totalChartCount"));
        assertFalse(facts.contains("completedChartCount"));
        assertFalse(facts.contains("activeChartCount"));
        assertFalse(charts.contains("chartId=99"));
    }

    @Test
    void omitsLegacyChartSummaryAndThreeOfFourNoTradeCharts() {
        List<SessionChartSummary> summaries = List.of(
                new SessionChartSummary(10L, 0, "TRADED", "Traded", "COMPLETED", 9, 2, 2, 1, true, BigDecimal.ZERO),
                new SessionChartSummary(11L, 1, "NO_TRADE_A", "A", "COMPLETED", 9, 0, 0, 0, false, BigDecimal.ZERO),
                new SessionChartSummary(12L, 2, "NO_TRADE_B", "B", "COMPLETED", 9, 0, 0, 0, false, BigDecimal.ZERO),
                new SessionChartSummary(13L, 3, "NO_TRADE_C", "C", "COMPLETED", 9, 0, 0, 0, false, BigDecimal.ZERO));
        SessionAiAnalysisRequest request = new SessionAiAnalysisRequest(
                1L, 2L, "RANDOM", "COMPLETED", 4, 4, 2, 0,
                summaries, List.of(), contextWithClosedAndOpenEpisodes(), qualitativeContext());

        String prompt = promptBuilder.buildSessionUserPrompt(request);
        assertFalse(prompt.contains("[기존 차트 요약]"));
        assertTrue(prompt.contains("ANALYSIS UNIT 1"));
        assertFalse(prompt.contains("symbol=TEST"));
        assertFalse(prompt.contains("NO_TRADE_A"));
        assertFalse(prompt.contains("NO_TRADE_B"));
        assertFalse(prompt.contains("NO_TRADE_C"));
    }

    @Test
    void systemPromptRequiresRepeatedEvidenceAndExplicitlyHandlesInsufficientEvidence() {
        String prompt = promptBuilder.buildSessionSystemPrompt();

        assertTrue(prompt.contains("같은 유형의 행동이 2회 이상"));
        assertTrue(prompt.contains("단 한 번의 episode/action을 반복 패턴이라고 부르지 마라"));
        assertTrue(prompt.contains("반복성은 확인되지 않았습니다") || prompt.contains("behaviorPatterns에서 제외"));
        assertTrue(prompt.contains("판단하기에 근거가 부족합니다"));
        assertTrue(prompt.contains("해당 항목은 이번 세션에서 확인되지 않았습니다"));
        assertTrue(prompt.contains("FOMO, 자신감 부족, 충동성, 시장에 대한 두려움은 명시적 사용자 evidence 없이 추론하지 마라"));
    }

    @Test
    void userPromptMarksChartCountsNeutralAndPreservesCanonicalEvidenceHierarchy() {
        SessionAiAnalysisRequest request = new SessionAiAnalysisRequest(
                1L, 2L, "RANDOM", "COMPLETED", 4, 4, 1, 0,
                List.of(), List.of(), contextWithClosedAndOpenEpisodes(), qualitativeContext());

        String prompt = promptBuilder.buildSessionUserPrompt(request);

        assertTrue(prompt.contains("미거래 chart와 거래 chart 수는 neutral metadata"));
        assertTrue(prompt.contains("USER CLAIM -> FACT -> GAP -> BEHAVIOR -> IMPLICATION -> NEXT DRILL"));
        assertTrue(prompt.contains("PRE-TRADE USER CLAIM (generic unless an action below has an exact relation)"));
        assertFalse(prompt.contains("거래가 특정 차트에만 몰렸는지"));
    }

    private SessionQualitativeEvidenceContext qualitativeContext() {
        EvidenceTimelineAnchor linked = new EvidenceTimelineAnchor(
                10, 1_000L, 101L, new TradeEpisodeReference(10L, 1), 700L, "LINKED_EVENT"
        );
        SnapshotAiEvidence snapshot = new SnapshotAiEvidence(
                80L, 1, Instant.ofEpochSecond(2), 70L,
                "support held", "pullback", "planned exit", "risk note", "memo", linked
        );
        NoteAiEvidence note = new NoteAiEvidence(
                90L, Instant.ofEpochSecond(3), "wait", null,
                new EvidenceTimelineAnchor(null, null, null, null, null, "UNRESOLVED")
        );
        return new SessionQualitativeEvidenceContext(
                1L, List.of(new ChartQualitativeEvidenceContext(
                        10L, false, true, List.of(snapshot), List.of(note)))
        );
    }

    private SessionAiDeterministicContext contextWithClosedAndOpenEpisodes() {
        TradeEpisodeAiContext closed = new TradeEpisodeAiContext(
                1,
                List.of(101L, 102L),
                List.of(103L),
                List.of(101L, 102L, 103L),
                1_000L,
                2_000L,
                2,
                1,
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(110),
                BigDecimal.valueOf(100),
                BigDecimal.TEN,
                5,
                true,
                BigDecimal.ZERO,
                700L,
                800L,
                new RiskPlanAiContext(
                        700L, BigDecimal.valueOf(95), BigDecimal.valueOf(120), true, 10, 1_000L
                ),
                new RiskPlanAiContext(
                        800L, BigDecimal.valueOf(98), BigDecimal.valueOf(130), false, 20, 2_000L
                )
        );
        TradeEpisodeAiContext open = new TradeEpisodeAiContext(
                2,
                List.of(104L),
                List.of(),
                List.of(104L),
                3_000L,
                null,
                1,
                0,
                BigDecimal.valueOf(5),
                BigDecimal.ZERO,
                BigDecimal.valueOf(90),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                false,
                BigDecimal.valueOf(5),
                null,
                null
        );
        ChartAiDeterministicContext chart = new ChartAiDeterministicContext(
                10L,
                0,
                20L,
                "TEST",
                "Test Symbol",
                "ETC",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 6, 1),
                120,
                119,
                "COMPLETED",
                false,
                true,
                4,
                2,
                true,
                List.of(closed, open)
        );
        SessionTradeStatistics statistics = new SessionTradeStatistics(
                2, 1, 1, 1, 0, 0, 1,
                BigDecimal.ONE,
                BigDecimal.valueOf(100),
                null,
                null,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(100),
                BigDecimal.TEN,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(2),
                BigDecimal.ONE,
                List.of(new TradeEpisodeReference(10L, 1)),
                List.of(),
                List.of(),
                List.of(new TradeEpisodeReference(10L, 2))
        );
        return new SessionAiDeterministicContext(
                1L, 9L, 2L, "RANDOM", "COMPLETED",
                1, 0, 1, 1, 4, statistics, List.of(chart)
        );
    }
}
