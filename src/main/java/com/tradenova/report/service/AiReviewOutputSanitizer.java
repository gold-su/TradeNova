package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Removes backend vocabulary from user-facing AI text while preserving its structure and Markdown emphasis. */
final class AiReviewOutputSanitizer {

    private static final List<Map.Entry<String, String>> NATURAL_LANGUAGE = List.of(
            Map.entry("stopLossPrice가", "손절가가"),
            Map.entry("stopLossPrice는", "손절가는"),
            Map.entry("takeProfitPrice가", "익절가가"),
            Map.entry("takeProfitPrice는", "익절가는"),
            Map.entry("currentAutoExitEnabled", "현재 자동 청산 설정"),
            Map.entry("historicalAutoExitEnabled", "거래 당시 자동 청산 설정"),
            Map.entry("autoExitEnabled", "자동 청산 설정"),
            Map.entry("stopLossPrice", "손절가"),
            Map.entry("takeProfitPrice", "익절가"),
            Map.entry("exitRiskPlan", "청산 계획"),
            Map.entry("stopLoss", "손절 기준"),
            Map.entry("takeProfit", "익절 기준"),
            Map.entry("autoExit", "자동 청산 설정"),
            Map.entry("riskNote", "리스크 관리 근거"),
            Map.entry("exitPlan", "청산 계획"),
            Map.entry("Backend Risk Plan Compliance", "계획 실행 일치 여부"),
            Map.entry("ACTION-TIME TRADE REASON", "거래 당시 기록한 이유"),
            Map.entry("FOLLOWED", "사전에 정한 기준을 따름"),
            Map.entry("COMPLETED", "완료됨"),
            Map.entry("field name", "내부 항목명"),
            Map.entry("entity", "내부 데이터"),
            Map.entry("DTO", "데이터 구조")
    );

    private AiReviewOutputSanitizer() {
    }

    static String sanitize(String text) {
        if (text == null || text.isEmpty()) return text;
        String sanitized = text
                .replaceAll("(?i)\\bsnapshot#\\d+", "사전 계획")
                .replaceAll("(?i)\\btradeId\\s*=\\s*\\d+\\s*", "")
                .replaceAll("(?i)\\bcompliance\\s*[:=]?\\s*UNKNOWN\\b", "계획 이행 여부를 명확히 판단할 근거가 부족함");
        for (Map.Entry<String, String> entry : NATURAL_LANGUAGE) {
            sanitized = sanitized.replace(entry.getKey(), entry.getValue());
        }
        sanitized = sanitized.replaceAll("\\bUNKNOWN\\b", "근거가 충분하지 않음");
        return sanitized;
    }

    static JsonNode sanitizeTextValues(JsonNode node) {
        if (node == null || node.isNull()) return node;
        if (node.isTextual()) return TextNode.valueOf(sanitize(node.textValue()));
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int index = 0; index < array.size(); index++) {
                array.set(index, sanitizeTextValues(array.get(index)));
            }
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                object.set(field.getKey(), sanitizeTextValues(field.getValue()));
            }
        }
        return node;
    }
}
