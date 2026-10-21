package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.report.entity.EventOrigin;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingEventFactsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void recognizesOnlyUserEvidenceLinkedToAnExecutedBuyOrSell() {
        assertTrue(TrainingEventFacts.isExecutedUserTrade(event("BUY", 11L, EventOrigin.USER)));
        assertTrue(TrainingEventFacts.isExecutedUserTrade(event("SELL", 12L, EventOrigin.USER)));
        assertFalse(TrainingEventFacts.isExecutedUserTrade(event("BUY", 11L, EventOrigin.SYSTEM)));
        assertFalse(TrainingEventFacts.isExecutedUserTrade(event("BUY", null, EventOrigin.USER)));
    }

    @Test
    void waitDecisionNeverCountsAsAnExecutedTrade() {
        assertFalse(TrainingEventFacts.isExecutedUserTrade(event("WAIT", null, EventOrigin.USER)));
        assertFalse(TrainingEventFacts.isExecutedUserTrade(event("WAIT", 13L, EventOrigin.USER)));
    }

    private TrainingEvent event(String side, Long tradeId, EventOrigin origin) {
        ObjectNode payload = mapper.createObjectNode().put("side", side);
        if (tradeId != null) payload.put("tradeId", tradeId);
        return TrainingEvent.builder()
                .type(Type.TRADE)
                .origin(origin)
                .payloadJson(payload)
                .build();
    }
}
