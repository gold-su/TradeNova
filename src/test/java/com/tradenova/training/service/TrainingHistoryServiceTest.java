package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.report.entity.*;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.symbol.dto.SymbolSector;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.dto.TrainingHistoryDetailResponse;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingHistoryServiceTest {
    private final TrainingSessionRepository sessions = mock(TrainingSessionRepository.class);
    private final TrainingSessionChartRepository charts = mock(TrainingSessionChartRepository.class);
    private final TrainingTradeRepository trades = mock(TrainingTradeRepository.class);
    private final ReportDocumentRepository documents = mock(ReportDocumentRepository.class);
    private final TrainingEventRepository events = mock(TrainingEventRepository.class);
    private final TrainingHistoryService service = new TrainingHistoryService(sessions, charts, trades, documents, events);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listsCompletedSessionsNewestFirstWithBatchCountsAndExactReviews() {
        TrainingSession newer = session(85L), older = session(83L);
        var first = chart(newer, 304L, 0, true);
        var refreshed = chart(newer, 305L, 0, false);
        var prior = chart(older, 301L, 0, true);
        when(sessions.findAllByUserIdAndStatusOrderByIdDesc(7L, TrainingStatus.COMPLETED))
                .thenReturn(List.of(newer, older));
        when(charts.findHistoryChartsBySessionIds(List.of(85L, 83L))).thenReturn(List.of(first, refreshed, prior));
        when(trades.countHistoryByChartIds(List.of(304L, 301L)))
                .thenReturn(List.of(count(304L, 2)));
        when(documents.countHistoryByChartIdsAndKind(7L, List.of(304L, 301L), ReportKind.SNAPSHOT))
                .thenReturn(List.of(count(304L, 1)));
        when(events.findAllByUserIdAndChartIdInAndTypeOrderByIdDesc(7L, List.of(304L, 301L), Type.AI))
                .thenReturn(List.of(ai(20L, 304L, "SESSION", 85L, 92)));
        when(events.findAllByUserIdAndChartIdInAndTypeAndSummaryOrderByIdDesc(
                7L, List.of(304L, 301L), Type.NOTE, "세션 종료"))
                .thenReturn(List.of(finished(85L, 304L, Instant.parse("2026-09-15T00:00:00Z"))));

        var result = service.list(7L);

        assertEquals(List.of(85L, 83L), result.stream().map(r -> r.sessionId()).toList());
        assertEquals(1, result.get(0).totalChartCount());
        assertEquals(1, result.get(0).completedChartCount());
        assertEquals(2, result.get(0).totalTradeCount());
        assertEquals(1, result.get(0).snapshotCount());
        assertEquals(92, result.get(0).sessionAiScore());
        assertTrue(result.get(0).hasSessionAiReview());
        assertEquals(Instant.parse("2026-09-15T00:00:00Z"), result.get(0).completedAt());
        assertNull(result.get(1).completedAt());
        assertFalse(result.get(1).hasSessionAiReview());
        assertNull(result.get(1).sessionAiScore());
        assertEquals(0, result.get(1).totalTradeCount());
        verify(trades, times(1)).countHistoryByChartIds(any());
        verify(documents, times(1)).countHistoryByChartIdsAndKind(any(), any(), any());
        verify(events, times(1)).findAllByUserIdAndChartIdInAndTypeOrderByIdDesc(any(), any(), any());
    }

    @Test
    void refreshedAttemptsDoNotDuplicateFourLogicalCharts() {
        TrainingSession session = session(85L);
        var original = chart(session, 303L, 0, false);
        var secondAttempt = chart(session, 304L, 0, false);
        secondAttempt.markRefreshed();
        var finalSlotZero = chart(session, 305L, 0, true);
        finalSlotZero.markRefreshed();
        var slotOne = chart(session, 306L, 1, true);
        var slotTwo = chart(session, 307L, 2, true);
        var slotThree = chart(session, 308L, 3, true);
        // Six persisted attempts for four logical slots. The repository query also filters active rows.
        var persisted = List.of(original, secondAttempt, finalSlotZero, slotOne, slotTwo, slotThree);
        when(sessions.findAllByUserIdAndStatusOrderByIdDesc(7L, TrainingStatus.COMPLETED))
                .thenReturn(List.of(session));
        when(sessions.findByIdAndUserId(85L, 7L)).thenReturn(Optional.of(session));
        when(charts.findHistoryChartsBySessionIds(List.of(85L))).thenReturn(persisted);
        List<Long> finalIds = List.of(305L, 306L, 307L, 308L);
        when(trades.countHistoryByChartIds(finalIds)).thenReturn(List.of(count(305L, 2)));
        when(documents.countHistoryByChartIdsAndKind(7L, finalIds, ReportKind.SNAPSHOT))
                .thenReturn(List.of(count(305L, 1)));

        var summary = service.list(7L).get(0);
        var detail = service.detail(7L, 85L);

        assertEquals(4, summary.totalChartCount());
        assertEquals(4, summary.completedChartCount());
        assertEquals(2, summary.totalTradeCount());
        assertEquals(1, summary.snapshotCount());
        assertEquals(4, detail.session().totalChartCount());
        assertEquals(4, detail.session().completedChartCount());
        assertEquals(finalIds, detail.charts().stream().map(c -> c.chartId()).toList());
        assertEquals(List.of(0, 1, 2, 3), detail.charts().stream().map(c -> c.chartIndex()).toList());
        assertTrue(detail.charts().get(0).refreshed());
        verify(trades, times(2)).countHistoryByChartIds(finalIds);
        verify(documents, times(2)).countHistoryByChartIdsAndKind(7L, finalIds, ReportKind.SNAPSHOT);
    }

    @Test
    void exactSessionAndChartPayloadsAreKeptSeparateAndLatestIdWins() {
        TrainingSession session = session(85L);
        var a = chart(session, 304L, 0, true);
        var b = chart(session, 305L, 1, true);
        when(sessions.findByIdAndUserId(85L, 7L)).thenReturn(Optional.of(session));
        when(charts.findHistoryChartsBySessionIds(List.of(85L))).thenReturn(List.of(a, b));
        when(trades.countHistoryByChartIds(List.of(304L, 305L)))
                .thenReturn(List.of(count(304L, 1)));
        when(documents.countHistoryByChartIdsAndKind(7L, List.of(304L, 305L), ReportKind.SNAPSHOT))
                .thenReturn(List.of());
        TrainingEvent sessionAi = ai(40L, 304L, "SESSION", 85L, 88);
        ((ObjectNode) sessionAi.getPayloadJson()).putArray("strengths").add("plan followed");
        TrainingEvent chartAi = ai(30L, 304L, "CHART", 304L, 73);
        // Same event chart, wrong payload chart: must not attach to either chart.
        TrainingEvent forgedChart = ai(90L, 304L, "CHART", 305L, 100);
        // Same event chart, another session ID: must not attach to this session.
        TrainingEvent forgedSession = ai(91L, 304L, "SESSION", 84L, 99);
        TrainingEvent wrongScope = ai(92L, 305L, "OTHER", 305L, 100);
        when(events.findAllByUserIdAndChartIdInAndTypeOrderByIdDesc(7L, List.of(304L, 305L), Type.AI))
                .thenReturn(List.of(ai(12L, 304L, "CHART", 304L, 45), sessionAi,
                        chartAi, forgedChart, forgedSession, wrongScope));
        when(events.findAllByUserIdAndChartIdInAndTypeAndSummaryOrderByIdDesc(
                7L, List.of(304L, 305L), Type.NOTE, "세션 종료"))
                .thenReturn(List.of());

        TrainingHistoryDetailResponse result = service.detail(7L, 85L);

        assertEquals(88, result.session().sessionAiScore());
        assertEquals("plan followed", result.sessionAiReview().payloadJson().path("strengths").get(0).asText());
        assertEquals(2, result.charts().size());
        assertEquals("T304", result.charts().get(0).symbolTicker());
        assertEquals("ETC", result.charts().get(0).sector());
        assertEquals(1, result.charts().get(0).tradeCount());
        assertEquals(0, result.charts().get(1).tradeCount());
        assertEquals(0, result.charts().get(1).snapshotCount());
        assertEquals(30L, result.charts().get(0).chartAiReview().id());
        assertEquals(73, result.charts().get(0).chartAiScore());
        assertFalse(result.charts().get(1).hasChartAiReview());
        assertNull(result.charts().get(1).chartAiReview());
    }

    @Test
    void rejectsForeignOrUnfinishedSessionBeforeLoadingChartsOrReviews() {
        assertThrows(CustomException.class, () -> service.detail(8L, 85L));
        TrainingSession unfinished = session(85L);
        unfinished.setStatus(TrainingStatus.IN_PROGRESS);
        when(sessions.findByIdAndUserId(85L, 7L)).thenReturn(Optional.of(unfinished));
        assertThrows(CustomException.class, () -> service.detail(7L, 85L));
        verifyNoInteractions(charts, trades, documents, events);
    }

    @Test
    void emptyOrLegacySessionHasZeroCountsAndNullReviews() {
        TrainingSession session = session(85L);
        when(sessions.findByIdAndUserId(85L, 7L)).thenReturn(Optional.of(session));
        when(charts.findHistoryChartsBySessionIds(List.of(85L))).thenReturn(List.of());

        TrainingHistoryDetailResponse result = service.detail(7L, 85L);

        assertEquals(0, result.session().totalChartCount());
        assertEquals(0, result.session().snapshotCount());
        assertNull(result.session().completedAt());
        assertNull(result.sessionAiReview());
        assertEquals(List.of(), result.charts());
        verifyNoInteractions(trades, documents, events);
    }

    @Test
    void refusesCrossUserOrCrossSessionEventsEvenWhenChartIdMatches() {
        TrainingSession session = session(85L);
        when(sessions.findByIdAndUserId(85L, 7L)).thenReturn(Optional.of(session));
        when(charts.findHistoryChartsBySessionIds(List.of(85L))).thenReturn(List.of(chart(session, 304L, 0, true)));
        when(events.findAllByUserIdAndChartIdInAndTypeOrderByIdDesc(7L, List.of(304L), Type.AI))
                .thenReturn(List.of(ai(5L, 304L, "SESSION", 84L, 90),
                        ai(6L, 304L, "CHART", 305L, 90), foreignAi()));
        when(events.findAllByUserIdAndChartIdInAndTypeAndSummaryOrderByIdDesc(
                7L, List.of(304L), Type.NOTE, "세션 종료"))
                .thenReturn(List.of(finished(84L, 304L, Instant.now())));

        var result = service.detail(7L, 85L);

        assertNull(result.sessionAiReview());
        assertNull(result.charts().get(0).chartAiReview());
        assertNull(result.session().completedAt());
    }

    private TrainingEvent foreignAi() {
        TrainingEvent event = ai(7L, 304L, "CHART", 304L, 90);
        event.setUserId(8L);
        return event;
    }

    private TrainingSession session(Long id) {
        return TrainingSession.builder().id(id).status(TrainingStatus.COMPLETED)
                .createdAt(OffsetDateTime.parse("2026-09-14T10:00:00+09:00")).build();
    }

    private TrainingSessionChart chart(TrainingSession session, Long id, int index, boolean completed) {
        return TrainingSessionChart.builder().id(id).session(session).chartIndex(index)
                .status(completed ? TrainingChartStatus.COMPLETED : TrainingChartStatus.IN_PROGRESS)
                .active(completed).refreshed(!completed)
                .symbol(Symbol.builder().ticker("T" + id).name("Symbol " + id)
                        .trainingSector(SymbolSector.ETC).build()).build();
    }

    private ChartCountProjection count(Long chartId, long amount) {
        return new ChartCountProjection() {
            public Long getChartId() { return chartId; }
            public Long getCount() { return amount; }
        };
    }

    private TrainingEvent ai(Long id, Long eventChartId, String scope, Long payloadId, int score) {
        ObjectNode payload = mapper.createObjectNode().put("analysisScope", scope).put("score", score)
                .put("summary", "saved review").put("generatedAt", "2026-09-15T00:00:00Z")
                .put("analysisVersion", 2);
        payload.put("SESSION".equals(scope) ? "sessionId" : "chartId", payloadId);
        return TrainingEvent.builder().id(id).userId(7L).chartId(eventChartId).type(Type.AI)
                .origin(EventOrigin.SYSTEM).summary("AI review").payloadJson(payload)
                .createdAt(Instant.parse("2026-09-15T00:00:00Z")).build();
    }

    private TrainingEvent finished(Long sessionId, Long chartId, Instant at) {
        return TrainingEvent.builder().id(50L).userId(7L).chartId(chartId).type(Type.NOTE)
                .origin(EventOrigin.SYSTEM).summary("세션 종료")
                .payloadJson(mapper.createObjectNode().put("sessionId", sessionId)).createdAt(at).build();
    }
}
