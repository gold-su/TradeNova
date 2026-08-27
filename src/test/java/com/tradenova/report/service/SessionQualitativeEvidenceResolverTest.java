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
