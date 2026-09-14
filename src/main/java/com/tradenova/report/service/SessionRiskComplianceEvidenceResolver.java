package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.dto.*;
import com.tradenova.report.entity.*;
import com.tradenova.training.analytics.TradeEpisodeReference;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/** Exact trade/history/event joins only; never infers a trigger from PnL or a nearby event. */
@Component
@RequiredArgsConstructor
public class SessionRiskComplianceEvidenceResolver {
    private final TrainingRiskRuleHistoryRepository historyRepository;

    public List<RiskComplianceAiEvidence> resolve(SessionAiDeterministicContext context,
                                                 List<TrainingTrade> trades, List<TrainingEvent> events) {
        Map<Long, TrainingTrade> byId = trades.stream()
                .filter(t -> Objects.equals(t.getAccountId(), context.accountId()))
                .collect(Collectors.toMap(TrainingTrade::getId, t -> t));
        Set<Long> historyIds = byId.values().stream().map(TrainingTrade::getRiskRuleHistoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, TrainingRiskRuleHistory> histories = historyIds.isEmpty() ? Map.of()
                : historyRepository.findAllByIdIn(historyIds).stream()
                .collect(Collectors.toMap(TrainingRiskRuleHistory::getId, h -> h));
        Map<Long, List<TrainingEvent>> eventsByTrade = events.stream()
                .filter(e -> Objects.equals(e.getUserId(), context.userId()))
                .filter(e -> e.getType() == Type.TRADE || e.getType() == Type.WARNING)
                .filter(e -> number(e.getPayloadJson(), "tradeId") != null)
                .collect(Collectors.groupingBy(e -> number(e.getPayloadJson(), "tradeId")));
        List<RiskComplianceAiEvidence> result = new ArrayList<>();
        for (var chart : context.charts()) {
            for (var episode : chart.episodes()) {
                var reference = new TradeEpisodeReference(chart.chartId(), episode.episodeIndex());
                BigDecimal position = BigDecimal.ZERO;
                boolean complete = true;
                int start = result.size();
                // allTradeIds is the canonical episode execution order, including intermediate exits.
                for (Long id : episode.allTradeIds()) {
                    TrainingTrade trade = byId.get(id);
                    if (trade == null || !Objects.equals(trade.getChartId(), chart.chartId())
                            || trade.getQty() == null || trade.getQty().signum() <= 0) {
                        complete = false;
                        continue;
                    }
                    if (trade.getSide() == TradeSide.BUY) {
                        position = position.add(trade.getQty());
                        continue;
                    }
                    if (trade.getSide() != TradeSide.SELL) { complete = false; continue; }
                    TrainingRiskRuleHistory plan = histories.get(trade.getRiskRuleHistoryId());
                    if (plan != null && (!Objects.equals(plan.getUserId(), context.userId())
                            || !Objects.equals(plan.getSessionId(), context.sessionId())
                            || !Objects.equals(plan.getAccountId(), context.accountId())
                            || !Objects.equals(plan.getChartId(), chart.chartId())
                            || plan.getCandleTime() == null || trade.getCandleTime() == null
                            || plan.getCandleTime() > trade.getCandleTime())) plan = null;
                    List<TrainingEvent> linked = eventsByTrade.getOrDefault(id, List.of()).stream()
                            .filter(e -> Objects.equals(e.getChartId(), chart.chartId())).toList();
                    List<TrainingEvent> actions = linked.stream()
                            .filter(e -> e.getType() == Type.TRADE && e.getOrigin() == EventOrigin.SYSTEM).toList();
                    TrainingEvent action = actions.size() == 1 ? actions.get(0) : null;
                    JsonNode payload = action == null ? null : action.getPayloadJson();
                    boolean exactAction = payload != null && "SELL".equals(payload.path("side").asText())
                            && Objects.equals(number(payload, "riskRuleHistoryId"), trade.getRiskRuleHistoryId())
                            && Objects.equals(number(payload, "candleTime"), trade.getCandleTime())
                            && decimalEquals(payload.get("qty"), trade.getQty())
                            && decimalEquals(payload.get("executedPrice"), trade.getPrice());
                    Boolean automatic = null;
                    if (exactAction && action.getOrigin() == EventOrigin.SYSTEM && payload.path("autoExit").isBoolean())
                        automatic = payload.get("autoExit").booleanValue();
                    Set<String> reasons = new HashSet<>();
                    if (Boolean.TRUE.equals(automatic) && riskReason(payload.path("autoExitReason").asText()))
                        reasons.add(payload.get("autoExitReason").asText());
                    linked.stream().filter(e -> e.getType() == Type.WARNING && e.getOrigin() == EventOrigin.SYSTEM)
                            .filter(e -> Objects.equals(number(e.getPayloadJson(), "candleTime"), trade.getCandleTime()))
                            .filter(e -> decimalEquals(e.getPayloadJson().get("qty"), trade.getQty())
                                    && decimalEquals(e.getPayloadJson().get("executedPrice"), trade.getPrice()))
                            .map(e -> e.getPayloadJson().path("reason").asText()).filter(this::riskReason)
                            .forEach(reasons::add);
                    String reason = reasons.size() == 1 ? reasons.iterator().next() : null;
                    Integer percent = plan == null || reason == null ? null : "STOP_LOSS".equals(reason)
                            ? plan.getStopLossExitPercent() : plan.getTakeProfitExitPercent();
                    boolean validPosition = complete && position.signum() > 0 && trade.getQty().compareTo(position) <= 0;
                    BigDecimal expected = validPosition && percent != null && percent >= 1 && percent <= 100
                            ? plannedQty(position, percent) : null;
                    BigDecimal actualPercent = validPosition ? trade.getQty().multiply(BigDecimal.valueOf(100))
                            .divide(position, 6, RoundingMode.HALF_UP).stripTrailingZeros() : null;
                    boolean configured = plan != null && reason != null && ("STOP_LOSS".equals(reason)
                            ? plan.getStopLossPrice() != null : plan.getTakeProfitPrice() != null);
                    String compliance = "UNKNOWN";
                    String basis = "Insufficient or conflicting exact execution/plan evidence; not a violation.";
                    if (reason == null && reasons.isEmpty()) {
                        basis = "No observed risk trigger linked to this exit; manual/forced exit alone is not a violation.";
                    } else if (configured && plan.isAutoExitEnabled() && expected != null && automatic != null) {
                        compliance = automatic && expected.compareTo(trade.getQty()) == 0 ? "FOLLOWED" : "NOT_FOLLOWED";
                        basis = "Exact risk trigger and trade-time plan compared with actual automatic execution and quantity; PnL excluded.";
                    }
                    result.add(new RiskComplianceAiEvidence(reference, id, trade.getRiskRuleHistoryId(),
                            plan == null ? null : plan.getStopLossPrice() != null,
                            plan == null ? null : plan.getTakeProfitPrice() != null,
                            plan == null ? null : plan.isAutoExitEnabled(), reason, percent, actualPercent,
                            expected, trade.getQty(), automatic, compliance, basis));
                    position = position.subtract(trade.getQty());
                    if (position.signum() < 0) complete = false;
                }
                if (result.size() == start) result.add(new RiskComplianceAiEvidence(reference, null, null,
                        null, null, null, null, null, null, null, null, null, "UNKNOWN",
                        "No observed exit/trigger evidence; plan existence alone does not establish non-compliance."));
            }
        }
        return List.copyOf(result);
    }

    private BigDecimal plannedQty(BigDecimal position, int percent) {
        // Match the existing risk execution floor / minimum-one-share semantics without mutating execution code.
        return percent == 100 ? position : position.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).max(BigDecimal.ONE).min(position);
    }

    private boolean riskReason(String reason) {
        return "STOP_LOSS".equals(reason) || "TAKE_PROFIT".equals(reason);
    }

    private static Long number(JsonNode payload, String field) {
        JsonNode node = payload == null ? null : payload.get(field);
        return node != null && node.isIntegralNumber() ? node.longValue() : null;
    }

    private boolean decimalEquals(JsonNode node, BigDecimal value) {
        return node != null && node.isNumber() && value != null && node.decimalValue().compareTo(value) == 0;
    }
}
