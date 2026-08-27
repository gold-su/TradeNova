package com.tradenova.report.service;

import com.tradenova.report.dto.ChartAiDeterministicContext;
import com.tradenova.report.dto.RiskPlanAiContext;
import com.tradenova.report.dto.SessionAiAnalysisRequest;
import com.tradenova.report.dto.SessionAiDeterministicContext;
import com.tradenova.report.dto.SessionSnapshotSummary;
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
        assertTrue(prompt.contains("activeChartCount=0"));
        assertTrue(prompt.contains("[Calculated Trade Statistics]"));
        assertTrue(prompt.contains("closedEpisodes=1, openEpisodes=1"));
        assertTrue(prompt.contains("winRate=1"));
        assertTrue(prompt.contains("payoffRatio=undefined"));
        assertFalse(prompt.contains("payoffRatio=0"));
        assertTrue(prompt.contains("expectancy=100"));
        assertTrue(prompt.contains("state=CLOSED"));
        assertTrue(prompt.contains("state=OPEN"));
        assertTrue(prompt.contains("active=false, refreshed=true"));
        assertTrue(prompt.contains("entryRiskPlan={historyId=700,stopLoss=95,takeProfit=120"));
        assertTrue(prompt.contains("exitRiskPlan={historyId=800,stopLoss=98,takeProfit=130,autoExit=false"));
        assertTrue(prompt.contains("entryRiskPlan=none"));
        assertTrue(prompt.contains("tradeRefs={entry:[101..102;count=2],exit:[103]}"));
        assertFalse(prompt.contains("101, 102"));
        assertTrue(prompt.contains("[User-authored Qualitative Evidence]"));
        assertTrue(prompt.contains("thesis:support held"));
        assertTrue(prompt.contains("NOTE#90"));
        assertTrue(prompt.contains("timeline=UNRESOLVED"));
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
