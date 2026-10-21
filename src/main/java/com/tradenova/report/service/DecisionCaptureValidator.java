package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.DecisionCurrentView;
import com.tradenova.report.dto.DecisionInvalidationAction;
import com.tradenova.report.dto.DecisionPlanRelation;
import com.tradenova.report.entity.Type;

final class DecisionCaptureValidator {
    private static final int MAX_TEXT_LENGTH = 500;
    private static final String CAPTURE_TAG = "DECISION_CAPTURE_V1";

    private DecisionCaptureValidator() { }

    static void validatePlan(JsonNode content) {
        if (!hasTag(content, CAPTURE_TAG)) return;
        requireEnum(content, "currentView", DecisionCurrentView.class, ErrorCode.INVALID_REQUEST);
        requireText(content, "entryCondition", ErrorCode.INVALID_REQUEST);
        requireText(content, "invalidationCondition", ErrorCode.INVALID_REQUEST);
        requireEnum(content, "ifInvalidated", DecisionInvalidationAction.class, ErrorCode.INVALID_REQUEST);
    }

    static void validateUserEvent(Type type, JsonNode payload) {
        if (type != Type.TRADE || payload == null || !payload.isObject() || !payload.has("planRelation")) return;

        DecisionPlanRelation relation = requireEnum(
                payload, "planRelation", DecisionPlanRelation.class, ErrorCode.INVALID_TRAINING_EVENT);
        String side = requiredText(payload, "side", ErrorCode.INVALID_TRAINING_EVENT);
        if (!side.equals("BUY") && !side.equals("SELL") && !side.equals("WAIT")) {
            throw new CustomException(ErrorCode.INVALID_TRAINING_EVENT);
        }

        if (relation == DecisionPlanRelation.FOLLOWED_PLAN || relation == DecisionPlanRelation.CHANGED_PLAN) {
            JsonNode snapshotId = payload.get("scenarioSnapshotId");
            if (snapshotId == null || !snapshotId.canConvertToLong() || snapshotId.longValue() <= 0
                    || !"SCENARIO".equals(text(payload.get("reasonMode")))) {
                throw new CustomException(ErrorCode.INVALID_TRAINING_EVENT);
            }
        }
        if (relation == DecisionPlanRelation.CHANGED_PLAN) {
            requireText(payload, "changeReason", ErrorCode.INVALID_TRAINING_EVENT);
        }
    }

    private static boolean hasTag(JsonNode content, String expected) {
        if (content == null || !content.path("tags").isArray()) return false;
        for (JsonNode tag : content.path("tags")) {
            if (tag.isTextual() && expected.equals(tag.textValue())) return true;
        }
        return false;
    }

    private static <E extends Enum<E>> E requireEnum(
            JsonNode payload, String field, Class<E> enumType, ErrorCode errorCode) {
        String value = requiredText(payload, field, errorCode);
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(errorCode);
        }
    }

    private static void requireText(JsonNode payload, String field, ErrorCode errorCode) {
        requiredText(payload, field, errorCode);
    }

    private static String requiredText(JsonNode payload, String field, ErrorCode errorCode) {
        String value = text(payload == null ? null : payload.get(field));
        if (value == null || value.length() > MAX_TEXT_LENGTH) throw new CustomException(errorCode);
        return value;
    }

    private static String text(JsonNode value) {
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue().trim()
                : null;
    }
}
