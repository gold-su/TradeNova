package com.tradenova.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.TrainingEventResponse;
import com.tradenova.report.entity.EventOrigin;
import com.tradenova.report.entity.ReportKind;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.training.dto.*;
import com.tradenova.training.entity.TrainingChartStatus;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only historical views assembled from owned sessions, grouped counts and saved AI events. */
@Service
@RequiredArgsConstructor
public class TrainingHistoryService {
    private final TrainingSessionRepository sessionRepository;
    private final TrainingSessionChartRepository chartRepository;
    private final TrainingTradeRepository tradeRepository;
    private final ReportDocumentRepository documentRepository;
    private final TrainingEventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<TrainingHistorySummaryResponse> list(Long userId) {
        List<TrainingSession> sessions = sessionRepository
                .findAllByUserIdAndStatusOrderByIdDesc(userId, TrainingStatus.COMPLETED);
        if (sessions.isEmpty()) return List.of();

        Evidence evidence = load(userId, sessions.stream().map(TrainingSession::getId).toList());
        return sessions.stream().map(session -> summary(session, evidence)).toList();
    }

    @Transactional(readOnly = true)
    public TrainingHistoryDetailResponse detail(Long userId, Long sessionId) {
        TrainingSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
        // Symbol disclosure follows the completed-session policy, never the blind workspace state.
        if (session.getStatus() != TrainingStatus.COMPLETED) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND);
        }

        Evidence evidence = load(userId, List.of(sessionId));
        List<TrainingHistoryChartResponse> charts = evidence.chartsBySession()
                .getOrDefault(sessionId, List.of()).stream().map(chart -> {
                    TrainingEvent review = evidence.chartAi().get(chart.getId());
                    return new TrainingHistoryChartResponse(
                            chart.getId(), chart.getChartIndex(), chart.getStatus().name(),
                            chart.isActive(), chart.isRefreshed(),
                            chart.getSymbol().getTicker(), chart.getSymbol().getName(),
                            chart.getSymbol().getTrainingSector().name(),
                            evidence.trades().getOrDefault(chart.getId(), 0L),
                            evidence.snapshots().getOrDefault(chart.getId(), 0L),
                            review != null, score(review), response(review));
                }).toList();
        return new TrainingHistoryDetailResponse(summary(session, evidence),
                response(evidence.sessionAi().get(sessionId)), charts);
    }

    private Evidence load(Long userId, List<Long> sessionIds) {
        // A refresh retains the previous row with active=false in the DB; My Page follows
        // the final logical chart in each slot, as the existing session summary does.
        List<TrainingSessionChart> charts = chartRepository.findHistoryChartsBySessionIds(sessionIds)
                .stream().filter(TrainingSessionChart::isActive).toList();
        Map<Long, List<TrainingSessionChart>> bySession = charts.stream()
                .collect(Collectors.groupingBy(chart -> chart.getSession().getId()));
        List<Long> chartIds = charts.stream().map(TrainingSessionChart::getId).toList();
        if (chartIds.isEmpty()) return new Evidence(bySession, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

        // One grouped query per count and one IN query per event kind, independent of session/chart count.
        Map<Long, Long> trades = counts(tradeRepository.countHistoryByChartIds(chartIds));
        Map<Long, Long> snapshots = counts(documentRepository.countHistoryByChartIdsAndKind(
                userId, chartIds, ReportKind.SNAPSHOT));
        Set<Long> ownedSessionIds = Set.copyOf(sessionIds);
        Set<Long> ownedChartIds = Set.copyOf(chartIds);
        Map<Long, Long> chartToSession = charts.stream().collect(Collectors.toMap(
                TrainingSessionChart::getId, chart -> chart.getSession().getId()));
        Map<Long, TrainingEvent> sessionAi = new HashMap<>();
        Map<Long, TrainingEvent> chartAi = new HashMap<>();
        for (TrainingEvent event : eventRepository.findAllByUserIdAndChartIdInAndTypeOrderByIdDesc(
                userId, chartIds, Type.AI)) {
            if (!userId.equals(event.getUserId()) || event.getOrigin() != EventOrigin.SYSTEM
                    || !ownedChartIds.contains(event.getChartId())) continue;
            JsonNode payload = event.getPayloadJson();
            if (payload == null || !payload.isObject()) continue;
            Long payloadSessionId = exactId(payload, "sessionId");
            Long payloadChartId = exactId(payload, "chartId");
            if ("SESSION".equals(payload.path("analysisScope").asText())
                    && payloadSessionId != null && ownedSessionIds.contains(payloadSessionId)
                    && payloadSessionId.equals(chartToSession.get(event.getChartId()))) {
                latest(sessionAi, payloadSessionId, event);
            } else if ("CHART".equals(payload.path("analysisScope").asText())
                    && payloadChartId != null && payloadChartId.equals(event.getChartId())) {
                latest(chartAi, payloadChartId, event);
            }
        }

        Map<Long, Instant> completed = new HashMap<>();
        for (TrainingEvent event : eventRepository.findAllByUserIdAndChartIdInAndTypeAndSummaryOrderByIdDesc(
                userId, chartIds, Type.NOTE, "세션 종료")) {
            if (!userId.equals(event.getUserId()) || event.getOrigin() != EventOrigin.SYSTEM
                    || !ownedChartIds.contains(event.getChartId())) continue;
            Long id = exactId(event.getPayloadJson(), "sessionId");
            if (id != null && id.equals(chartToSession.get(event.getChartId()))
                    && event.getCreatedAt() != null) {
                completed.merge(id, event.getCreatedAt(), (first, next) -> first.isAfter(next) ? first : next);
            }
        }
        return new Evidence(bySession, trades, snapshots, sessionAi, chartAi, completed);
    }

    private Map<Long, Long> counts(List<ChartCountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(ChartCountProjection::getChartId,
                ChartCountProjection::getCount));
    }

    private void latest(Map<Long, TrainingEvent> result, Long key, TrainingEvent candidate) {
        result.merge(key, candidate, (old, next) -> old.getId() >= next.getId() ? old : next);
    }

    private Long exactId(JsonNode payload, String name) {
        if (payload == null || !payload.isObject()) return null;
        JsonNode value = payload.get(name);
        return value != null && value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0
                ? value.longValue() : null;
    }

    private TrainingHistorySummaryResponse summary(TrainingSession session, Evidence evidence) {
        List<TrainingSessionChart> charts = evidence.chartsBySession().getOrDefault(session.getId(), List.of());
        TrainingEvent review = evidence.sessionAi().get(session.getId());
        return new TrainingHistorySummaryResponse(
                session.getId(), session.getStatus().name(), session.getCreatedAt(),
                evidence.completedAt().get(session.getId()), charts.size(),
                (int) charts.stream().filter(c -> c.getStatus() == TrainingChartStatus.COMPLETED).count(),
                charts.stream().mapToLong(c -> evidence.trades().getOrDefault(c.getId(), 0L)).sum(),
                charts.stream().mapToLong(c -> evidence.snapshots().getOrDefault(c.getId(), 0L)).sum(),
                review != null, score(review));
    }

    private Integer score(TrainingEvent event) {
        if (event == null || event.getPayloadJson() == null) return null;
        JsonNode score = event.getPayloadJson().get("score");
        return score != null && score.isIntegralNumber() && score.canConvertToInt() ? score.intValue() : null;
    }

    private TrainingEventResponse response(TrainingEvent event) {
        return event == null ? null : new TrainingEventResponse(
                event.getId(), event.getChartId(), event.getType().name(), event.getSummary(),
                event.getPayloadJson(), event.getCreatedAt());
    }

    private record Evidence(
            Map<Long, List<TrainingSessionChart>> chartsBySession,
            Map<Long, Long> trades,
            Map<Long, Long> snapshots,
            Map<Long, TrainingEvent> sessionAi,
            Map<Long, TrainingEvent> chartAi,
            Map<Long, Instant> completedAt
    ) {}
}
