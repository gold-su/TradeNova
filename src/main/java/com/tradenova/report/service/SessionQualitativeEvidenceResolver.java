package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.dto.*;
import com.tradenova.report.entity.EventOrigin;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.training.analytics.TradeEpisodeReference;
import org.springframework.stereotype.Component;

import java.util.*;

/** Resolves only explicit canonical links; temporal proximity is never treated as proof. */
@Component
public class SessionQualitativeEvidenceResolver {

    public SessionQualitativeEvidenceContext resolve(
            SessionAiDeterministicContext deterministic,
            List<ReportDocument> snapshots,
            List<TrainingEvent> events
    ) {
        Map<Long, TrainingEvent> eventsById = new HashMap<>();
        events.forEach(event -> eventsById.put(event.getId(), event));
        Map<Long, TradeEpisodeReference> episodeByTradeId = episodeByTradeId(deterministic);
        Map<Long, List<ReportDocument>> snapshotsByChart = new HashMap<>();
        snapshots.forEach(snapshot -> snapshotsByChart
                .computeIfAbsent(snapshot.getChartId(), ignored -> new ArrayList<>()).add(snapshot));
        Map<Long, List<TrainingEvent>> notesByChart = new HashMap<>();
        events.stream()
                .filter(event -> event.getType() == Type.NOTE && event.getOrigin() == EventOrigin.USER)
                .forEach(event -> notesByChart
                        .computeIfAbsent(event.getChartId(), ignored -> new ArrayList<>()).add(event));

        List<ChartQualitativeEvidenceContext> charts = deterministic.charts().stream()
                .map(chart -> new ChartQualitativeEvidenceContext(
                        chart.chartId(), chart.active(), chart.refreshed(),
                        snapshotsByChart.getOrDefault(chart.chartId(), List.of()).stream()
                                .sorted(Comparator.comparing(ReportDocument::getCreatedAt,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                .map(snapshot -> snapshot(snapshot, eventsById, episodeByTradeId)).toList(),
                        notesByChart.getOrDefault(chart.chartId(), List.of()).stream()
                                .sorted(Comparator.comparing(TrainingEvent::getCreatedAt,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                .map(note -> note(note, episodeByTradeId)).toList()
                )).toList();
        return new SessionQualitativeEvidenceContext(deterministic.sessionId(), charts);
    }

    private SnapshotAiEvidence snapshot(ReportDocument document,
                                        Map<Long, TrainingEvent> eventsById,
                                        Map<Long, TradeEpisodeReference> episodeByTradeId) {
        TrainingEvent linked = document.getLinkedEventId() == null
                ? null : eventsById.get(document.getLinkedEventId());
        EvidenceTimelineAnchor anchor = linked != null && linked.getChartId().equals(document.getChartId())
                ? anchor(linked.getPayloadJson(), episodeByTradeId, "LINKED_EVENT") : unresolved();
        JsonNode content = document.getContentJson();
        return new SnapshotAiEvidence(
                document.getId(), document.getVersion(), document.getCreatedAt(), document.getLinkedEventId(),
                text(content, "thesis"), text(content, "entryReason"), text(content, "exitPlan"),
                text(content, "riskNote"), text(content, "freeNote"), anchor
        );
    }

    private NoteAiEvidence note(TrainingEvent event,
                                Map<Long, TradeEpisodeReference> episodeByTradeId) {
        JsonNode payload = event.getPayloadJson();
        return new NoteAiEvidence(
                event.getId(), event.getCreatedAt(), event.getSummary(), noteDetail(payload),
                hasTimelineField(payload) ? anchor(payload, episodeByTradeId, "EXPLICIT_NOTE_PAYLOAD") : unresolved()
        );
    }

    private EvidenceTimelineAnchor anchor(JsonNode payload,
                                          Map<Long, TradeEpisodeReference> episodeByTradeId,
                                          String resolution) {
        Long tradeId = number(payload, "tradeId");
        return new EvidenceTimelineAnchor(
                integer(payload, "progressIndex"), firstNumber(payload, "candleTime", "currentCandleTime"),
                tradeId, tradeId == null ? null : episodeByTradeId.get(tradeId),
                number(payload, "riskRuleHistoryId"), resolution
        );
    }

    private EvidenceTimelineAnchor unresolved() {
        return new EvidenceTimelineAnchor(null, null, null, null, null, "UNRESOLVED");
    }

    private Map<Long, TradeEpisodeReference> episodeByTradeId(SessionAiDeterministicContext context) {
        Map<Long, TradeEpisodeReference> result = new HashMap<>();
        for (ChartAiDeterministicContext chart : context.charts()) {
            for (TradeEpisodeAiContext episode : chart.episodes()) {
                TradeEpisodeReference reference = new TradeEpisodeReference(chart.chartId(), episode.episodeIndex());
                episode.allTradeIds().forEach(tradeId -> result.put(tradeId, reference));
            }
        }
        return result;
    }

    private boolean hasTimelineField(JsonNode payload) {
        return payload != null && (payload.hasNonNull("tradeId") || payload.hasNonNull("riskRuleHistoryId")
                || payload.hasNonNull("progressIndex") || payload.hasNonNull("candleTime")
                || payload.hasNonNull("currentCandleTime"));
    }

    private String noteDetail(JsonNode payload) {
        for (String field : List.of("note", "text", "content", "message")) {
            String value = text(payload, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long firstNumber(JsonNode node, String... fields) {
        for (String field : fields) {
            Long value = number(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private Long number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isIntegralNumber() ? null : value.longValue();
    }

    private Integer integer(JsonNode node, String field) {
        Long value = number(node, field);
        return value == null ? null : Math.toIntExact(value);
    }
}
