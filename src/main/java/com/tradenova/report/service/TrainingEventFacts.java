package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.entity.EventOrigin;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;

/** Shared, defensive classification for flexible training-event payloads. */
public final class TrainingEventFacts {
    private TrainingEventFacts() { }

    public static boolean isExecutedUserTrade(TrainingEvent event) {
        if (event == null || event.getType() != Type.TRADE || event.getOrigin() != EventOrigin.USER) return false;
        JsonNode payload = event.getPayloadJson();
        if (payload == null || !payload.isObject()) return false;
        String side = payload.path("side").asText("");
        JsonNode tradeId = payload.get("tradeId");
        return ("BUY".equals(side) || "SELL".equals(side))
                && tradeId != null && tradeId.isIntegralNumber()
                && tradeId.canConvertToLong() && tradeId.longValue() > 0;
    }
}
