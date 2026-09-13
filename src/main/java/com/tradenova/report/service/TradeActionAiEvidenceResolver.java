package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.dto.EvidenceTimelineAnchor;
import com.tradenova.report.dto.TradeActionAiEvidence;
import com.tradenova.report.dto.TradeReasonAiEvidence;
import com.tradenova.report.entity.EventOrigin;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.training.analytics.TradeEpisodeReference;
import com.tradenova.training.entity.TrainingTrade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Defensively parses USER TRADE payloads and links them only by canonical trade id. */
@Component
public class TradeActionAiEvidenceResolver {

    public TradeActionAiEvidence resolve(TrainingTrade trade, List<TrainingEvent> events) {
        if (trade == null || trade.getId() == null || trade.getChartId() == null) return null;
        for (TrainingEvent event : events == null ? List.<TrainingEvent>of() : events) {
            TradeActionAiEvidence evidence = parse(event, Map.of());
            if (evidence != null
                    && trade.getId().equals(evidence.tradeId())
                    && trade.getChartId().equals(evidence.chartId())
                    && trade.getSide().name().equals(evidence.side())) {
                return evidence;
            }
        }
        return null;
    }

    public TradeActionAiEvidence parse(
            TrainingEvent event,
            Map<Long, TradeEpisodeReference> episodeByTradeId
    ) {
        if (event == null || event.getType() != Type.TRADE || event.getOrigin() != EventOrigin.USER
                || event.getChartId() == null) return null;
        JsonNode payload = event.getPayloadJson();
        if (payload == null || !payload.isObject()) return null;
        Long tradeId = integral(payload.get("tradeId"));
        String side = text(payload.get("side"));
        JsonNode reasonNodes = payload.get("reasons");
        if (tradeId == null || tradeId <= 0 || side == null
                || !(side.equals("BUY") || side.equals("SELL"))
                || reasonNodes == null || !reasonNodes.isArray()) return null;

        List<TradeReasonAiEvidence> reasons = new ArrayList<>();
        for (JsonNode reason : reasonNodes) {
            if (!reason.isObject()) continue;
            String title = text(reason.get("title"));
            String entryReason = text(reason.get("entryReason"));
            String riskNote = text(reason.get("riskNote"));
            if (title == null && entryReason == null && riskNote == null) continue;
            reasons.add(new TradeReasonAiEvidence(title, entryReason, riskNote, instant(reason.get("createdAt"))));
        }
        if (reasons.isEmpty()) return null;

        TradeEpisodeReference episode = episodeByTradeId.get(tradeId);
        if (episode != null && !event.getChartId().equals(episode.chartId())) return null;
        EvidenceTimelineAnchor timeline = new EvidenceTimelineAnchor(
                null, integral(payload.get("candleTime")), tradeId, episode, null,
                episode == null ? "EXPLICIT_TRADE_ID_UNRESOLVED" : "EXPLICIT_TRADE_ID"
        );
        return new TradeActionAiEvidence(
                event.getId(), event.getChartId(), tradeId, side, integral(payload.get("candleTime")),
                decimal(payload.get("qty")), decimal(payload.get("price")), event.getCreatedAt(), reasons, timeline
        );
    }

    private Long integral(JsonNode node) {
        return node != null && node.isIntegralNumber() ? node.longValue() : null;
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || !node.isNumber()) return null;
        try { return node.decimalValue(); } catch (ArithmeticException exception) { return null; }
    }

    private String text(JsonNode node) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) return null;
        return node.textValue().trim();
    }

    private Instant instant(JsonNode node) {
        String value = text(node);
        if (value == null) return null;
        try { return Instant.parse(value); } catch (DateTimeParseException exception) { return null; }
    }
}
