package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.report.dto.*;
import com.tradenova.report.entity.*;
import com.tradenova.training.analytics.SessionTradeStatistics;
import com.tradenova.training.analytics.TradeEpisodeReference;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionQualitativeEvidenceResolverTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SessionQualitativeEvidenceResolver resolver = new SessionQualitativeEvidenceResolver();

    @Test
    void resolvesLinkedSnapshotAndLeavesUnlinkedUserNoteUnresolved() {
        ObjectNode payload = mapper.createObjectNode().put("tradeId", 101L)
                .put("riskRuleHistoryId", 70L).put("candleTime", 1_000L).put("progressIndex", 10);
        TrainingEvent trade = event(50L, Type.TRADE, EventOrigin.SYSTEM, "buy", payload, 2);
        ReportDocument snapshot = ReportDocument.builder().id(60L).userId(1L).chartId(10L)
                .kind(ReportKind.SNAPSHOT).version(1).linkedEventId(50L)
                .contentJson(mapper.createObjectNode().put("riskNote", "손절선 유지"))
                .createdAt(Instant.ofEpochSecond(3)).build();
        TrainingEvent note = event(70L, Type.NOTE, EventOrigin.USER, "조금 더 기다리기", null, 5);

        ChartQualitativeEvidenceContext chart = resolver.resolve(
                context(), List.of(snapshot), List.of(trade, note)).charts().get(0);

        assertEquals(new TradeEpisodeReference(10L, 1),
                chart.snapshots().get(0).timeline().episodeReference());
        assertEquals(70L, chart.snapshots().get(0).timeline().riskRuleHistoryId());
        assertEquals("UNRESOLVED", chart.notes().get(0).timeline().resolution());
    }

    @Test
    void excludesSystemAndLegacyNotesButPreservesInactiveChartSnapshot() {
        ReportDocument snapshot = ReportDocument.builder().id(60L).userId(1L).chartId(10L)
                .kind(ReportKind.SNAPSHOT).version(1).contentJson(mapper.createObjectNode().put("thesis", "past"))
                .createdAt(Instant.ofEpochSecond(3)).build();
        List<TrainingEvent> events = List.of(
                event(1L, Type.WARNING, EventOrigin.SYSTEM, "warning", null, 1),
                event(2L, Type.NOTE, EventOrigin.SYSTEM, "forced", null, 2),
                event(3L, Type.NOTE, null, "legacy", null, 3));

        ChartQualitativeEvidenceContext chart = resolver.resolve(context(), List.of(snapshot), events)
                .charts().get(0);
        assertFalse(chart.active());
        assertTrue(chart.refreshed());
        assertEquals(1, chart.snapshots().size());
        assertTrue(chart.notes().isEmpty());
        assertEquals("UNRESOLVED", chart.snapshots().get(0).timeline().resolution());
    }

    @Test
    void exposesUserTradeReasonWithExactEpisodeReference() {
        ObjectNode payload = tradePayload(101L, "BUY");
        TrainingEvent userTrade = event(80L, Type.TRADE, EventOrigin.USER, "buy", payload, 4);

        ChartQualitativeEvidenceContext chart = resolver.resolve(context(), List.of(), List.of(userTrade))
                .charts().get(0);

        assertEquals(1, chart.tradeActions().size());
        TradeActionAiEvidence evidence = chart.tradeActions().get(0);
        assertEquals(101L, evidence.tradeId());
        assertEquals("planned confirmation", evidence.reasons().get(0).entryReason());
        assertEquals(new TradeEpisodeReference(10L, 1), evidence.timeline().episodeReference());
    }

    @Test
    void doesNotFabricateEvidenceForEmptySystemOrMalformedTradeEvents() {
        ObjectNode empty = mapper.createObjectNode().put("tradeId", 101L).put("side", "BUY");
        empty.putArray("reasons");
        ObjectNode malformed = mapper.createObjectNode().put("tradeId", 101L).put("side", "BUY")
                .put("reasons", "not-an-array");
        List<TrainingEvent> events = List.of(
                event(80L, Type.TRADE, EventOrigin.USER, "empty", empty, 4),
                event(81L, Type.TRADE, EventOrigin.SYSTEM, "system", tradePayload(101L, "BUY"), 5),
                event(82L, Type.TRADE, null, "legacy", tradePayload(101L, "BUY"), 6),
                event(83L, Type.TRADE, EventOrigin.USER, "malformed", malformed, 7));

        assertTrue(resolver.resolve(context(), List.of(), events).charts().get(0).tradeActions().isEmpty());
    }

    @Test
    void rejectsTradeIdWhoseCanonicalEpisodeBelongsToAnotherChart() {
        TrainingEvent inconsistent = event(80L, Type.TRADE, EventOrigin.USER, "buy",
                tradePayload(101L, "BUY"), 4);
        inconsistent.setChartId(99L);

        SessionQualitativeEvidenceContext evidence = resolver.resolve(context(), List.of(), List.of(inconsistent));

        assertTrue(evidence.charts().get(0).tradeActions().isEmpty());
    }

    @Test
    void mapsEachTradeToItsExplicitScenarioWithoutTimestampGuessing() {
        ReportDocument older = scenario(55L, 1, "first plan");
        ReportDocument newer = scenario(56L, 2, "second plan");
        ObjectNode firstPayload = tradePayload(101L, "BUY").put("reasonMode", "SCENARIO")
                .put("scenarioSnapshotId", 55L);
        ObjectNode secondPayload = tradePayload(102L, "SELL").put("reasonMode", "SCENARIO")
                .put("scenarioSnapshotId", 56L);

        ChartQualitativeEvidenceContext chart = resolver.resolve(context(), List.of(newer, older), List.of(
                event(80L, Type.TRADE, EventOrigin.USER, "buy", firstPayload, 100),
                event(81L, Type.TRADE, EventOrigin.USER, "sell", secondPayload, 3)))
                .charts().get(0);

        assertEquals(55L, chart.tradeActions().get(1).linkedScenarioPlan().snapshotId());
        assertEquals("first plan", chart.tradeActions().get(1).linkedScenarioPlan().entryReason());
        assertEquals(56L, chart.tradeActions().get(0).linkedScenarioPlan().snapshotId());
    }

    private ReportDocument scenario(Long id, int version, String entryReason) {
        ObjectNode content = mapper.createObjectNode().put("entryReason", entryReason);
        content.putArray("tags").add("SCENARIO");
        return ReportDocument.builder().id(id).userId(1L).chartId(10L).kind(ReportKind.SNAPSHOT)
                .version(version).contentJson(content).createdAt(Instant.ofEpochSecond(version)).build();
    }

    private ObjectNode tradePayload(Long tradeId, String side) {
        ObjectNode payload = mapper.createObjectNode().put("tradeId", tradeId).put("side", side)
                .put("qty", 2).put("price", 10.5).put("candleTime", 1_000L)
                .put("reasonCount", 1).put("savedForAiReview", true).put("reasonVersion", 2);
        payload.putArray("reasons").addObject().put("title", "setup")
                .put("entryReason", "planned confirmation").put("riskNote", "defined stop")
                .put("createdAt", "2026-01-01T00:00:00Z");
        return payload;
    }

    private TrainingEvent event(Long id, Type type, EventOrigin origin, String summary,
                                ObjectNode payload, long second) {
        return TrainingEvent.builder().id(id).userId(1L).chartId(10L).type(type).origin(origin)
                .summary(summary).payloadJson(payload).createdAt(Instant.ofEpochSecond(second)).build();
    }

    private SessionAiDeterministicContext context() {
        TradeEpisodeAiContext episode = new TradeEpisodeAiContext(
                1, List.of(101L), List.of(102L), List.of(101L, 102L), 1_000L, 2_000L,
                1, 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(11),
                BigDecimal.ONE, BigDecimal.TEN, 1, true, BigDecimal.ZERO, 70L, 71L);
        ChartAiDeterministicContext chart = new ChartAiDeterministicContext(
                10L, 0, 20L, "TEST", "Test", "ETC", LocalDate.now(), LocalDate.now(),
                10, 9, "COMPLETED", false, true, 2, 1, true, List.of(episode));
        SessionTradeStatistics stats = new SessionTradeStatistics(
                1, 1, 0, 1, 0, 0, 1, BigDecimal.ONE, BigDecimal.ONE, null, null,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, List.of(new TradeEpisodeReference(10L, 1)), List.of(), List.of(), List.of());
        return new SessionAiDeterministicContext(
                5L, 1L, 2L, "RANDOM", "COMPLETED", 1, 0, 1, 1, 2, stats, List.of(chart));
    }
}
