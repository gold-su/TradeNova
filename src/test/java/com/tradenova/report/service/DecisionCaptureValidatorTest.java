package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.report.entity.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionCaptureValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsCompleteDecisionCaptureAndLegacyScenario() {
        assertDoesNotThrow(() -> DecisionCaptureValidator.validatePlan(decisionPlan()));
        assertDoesNotThrow(() -> DecisionCaptureValidator.validatePlan(
                mapper.createObjectNode().put("thesis", "legacy")));
    }

    @Test
    void rejectsMissingPlanFieldAndInvalidEnum() {
        ObjectNode missing = decisionPlan();
        missing.remove("entryCondition");
        ObjectNode invalid = decisionPlan().put("currentView", "UP_ONLY");

        assertThrows(CustomException.class, () -> DecisionCaptureValidator.validatePlan(missing));
        assertThrows(CustomException.class, () -> DecisionCaptureValidator.validatePlan(invalid));
    }

    @Test
    void validatesExplicitActionRelationAndChangedReason() {
        assertDoesNotThrow(() -> DecisionCaptureValidator.validateUserEvent(
                Type.TRADE, action("FOLLOWED_PLAN").put("scenarioSnapshotId", 12L).put("reasonMode", "SCENARIO")));
        assertDoesNotThrow(() -> DecisionCaptureValidator.validateUserEvent(
                Type.TRADE, action("CHANGED_PLAN").put("scenarioSnapshotId", 12L)
                        .put("reasonMode", "SCENARIO").put("changeReason", "거래량 돌파 확인")));
        assertDoesNotThrow(() -> DecisionCaptureValidator.validateUserEvent(
                Type.TRADE, action("UNPLANNED")));

        assertThrows(CustomException.class, () -> DecisionCaptureValidator.validateUserEvent(
                Type.TRADE, action("CHANGED_PLAN").put("scenarioSnapshotId", 12L).put("reasonMode", "SCENARIO")));
        assertThrows(CustomException.class, () -> DecisionCaptureValidator.validateUserEvent(
                Type.TRADE, action("FOLLOWED_PLAN")));
        assertThrows(CustomException.class, () -> DecisionCaptureValidator.validateUserEvent(
                Type.TRADE, action("UNKNOWN")));
    }

    private ObjectNode decisionPlan() {
        ObjectNode plan = mapper.createObjectNode()
                .put("currentView", "NEUTRAL")
                .put("entryCondition", "고점 돌파와 거래량 확인")
                .put("invalidationCondition", "박스권 재진입")
                .put("ifInvalidated", "REASSESS");
        plan.putArray("tags").add("SCENARIO").add("DECISION_CAPTURE_V1");
        return plan;
    }

    private ObjectNode action(String relation) {
        ObjectNode payload = mapper.createObjectNode()
                .put("side", "BUY")
                .put("planRelation", relation);
        payload.putArray("reasons");
        return payload;
    }
}
