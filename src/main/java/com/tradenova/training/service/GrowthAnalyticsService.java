package com.tradenova.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.entity.EventOrigin;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.training.analytics.GrowthAnalyticsCalculator;
import com.tradenova.training.dto.GrowthOverviewResponse;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.ChartCountProjection;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrowthAnalyticsService {
    private final TrainingSessionRepository sessionRepository;
    private final TrainingSessionChartRepository chartRepository;
    private final TrainingTradeRepository tradeRepository;
    private final ReportDocumentRepository documentRepository;
    private final TrainingEventRepository eventRepository;
    private final TrainingRiskRuleHistoryRepository riskHistoryRepository;
    private final GrowthAnalyticsCalculator calculator;

    @Transactional(readOnly = true)
    public GrowthOverviewResponse overview(Long userId, Integer limit) {
        List<TrainingSession> all = sessionRepository.findAllByUserIdAndStatusOrderByIdDesc(userId, TrainingStatus.COMPLETED);
        String period = limit == null ? "ALL" : "LAST_" + limit;
        if (all.isEmpty()) return calculator.calculate(period, limit, List.of(), List.of());

        List<Long> sessionIds = all.stream().map(TrainingSession::getId).toList();
        List<TrainingSessionChart> charts = chartRepository.findHistoryChartsBySessionIds(sessionIds).stream()
                .filter(TrainingSessionChart::isActive).toList();
        List<Long> chartIds = charts.stream().map(TrainingSessionChart::getId).toList();
        Map<Long, Long> chartToSession = charts.stream().collect(Collectors.toMap(
                TrainingSessionChart::getId, chart -> chart.getSession().getId()));
        Map<Long, Long> tradesByChart = chartIds.isEmpty() ? Map.of() : tradeRepository.countHistoryByChartIds(chartIds)
                .stream().collect(Collectors.toMap(ChartCountProjection::getChartId, ChartCountProjection::getCount));

        Set<Long> planSessions = new HashSet<>();
        if (!chartIds.isEmpty()) {
            for (ReportDocument document : documentRepository.findAllByUserIdAndChartIdInAndKindOrderByCreatedAtDesc(userId, chartIds, ReportKind.SNAPSHOT)) {
                if (isScenario(document.getContentJson())) planSessions.add(chartToSession.get(document.getChartId()));
            }
        }
        Set<Long> riskSessions = riskHistoryRepository.findAllByUserIdAndSessionIdIn(userId, sessionIds).stream()
                .map(history -> history.getSessionId()).collect(Collectors.toSet());

        Map<Long, Integer> aiScore = new HashMap<>();
        Map<Long, Instant> completedAt = new HashMap<>();
        Map<Long, Long> userTrades = new HashMap<>();
        Map<Long, Long> reasonedTrades = new HashMap<>();
        if (!chartIds.isEmpty()) {
            for (TrainingEvent event : eventRepository.findAllByUserIdAndChartIdInOrderByIdAsc(userId, chartIds)) {
                Long sessionId = chartToSession.get(event.getChartId());
                if (sessionId == null) continue;
                JsonNode payload = event.getPayloadJson();
                if (event.getType() == Type.TRADE && event.getOrigin() == EventOrigin.USER) {
                    userTrades.merge(sessionId, 1L, Long::sum);
                    if (hasReasons(payload)) reasonedTrades.merge(sessionId, 1L, Long::sum);
                } else if (event.getType() == Type.AI && event.getOrigin() == EventOrigin.SYSTEM
                        && "SESSION".equals(text(payload, "analysisScope")) && sessionId.equals(id(payload, "sessionId"))) {
                    Integer score = integer(payload, "score");
                    if (score != null) aiScore.put(sessionId, score);
                } else if (event.getType() == Type.NOTE && "세션 종료".equals(event.getSummary())
                        && sessionId.equals(id(payload, "sessionId")) && event.getCreatedAt() != null) {
                    completedAt.put(sessionId, event.getCreatedAt());
                }
            }
        }

        Map<Long, TrainingSession> byId = all.stream().collect(Collectors.toMap(TrainingSession::getId, Function.identity()));
        List<GrowthAnalyticsCalculator.SessionFact> facts = sessionIds.stream().map(sessionId -> {
            long tradeCount = charts.stream().filter(c -> c.getSession().getId().equals(sessionId))
                    .mapToLong(c -> tradesByChart.getOrDefault(c.getId(), 0L)).sum();
            return new GrowthAnalyticsCalculator.SessionFact(sessionId,
                    completedAt.getOrDefault(sessionId, byId.get(sessionId).getCreatedAt().toInstant()), tradeCount,
                    userTrades.getOrDefault(sessionId, 0L), reasonedTrades.getOrDefault(sessionId, 0L),
                    planSessions.contains(sessionId), riskSessions.contains(sessionId), aiScore.get(sessionId));
        }).toList();
        List<GrowthAnalyticsCalculator.SessionFact> periodFacts = limit == null
                ? facts : facts.subList(0, Math.min(limit, facts.size()));
        return calculator.calculate(period, limit, facts, periodFacts);
    }

    private boolean isScenario(JsonNode content) {
        JsonNode tags = content == null ? null : content.get("tags");
        if (tags == null || !tags.isArray()) return false;
        for (JsonNode tag : tags) if (tag.isTextual() && "SCENARIO".equals(tag.textValue())) return true;
        return false;
    }

    private boolean hasReasons(JsonNode payload) {
        JsonNode reasons = payload == null ? null : payload.get("reasons");
        return reasons != null && reasons.isArray() && !reasons.isEmpty();
    }

    private String text(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private Long id(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToLong() ? value.longValue() : null;
    }

    private Integer integer(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
    }
}
