package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes backend vocabulary from user-facing AI text while preserving its structure and Markdown emphasis. */
public final class AiReviewOutputSanitizer {

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
            Map.entry("TradeReasonAiEvidence", ""),
            Map.entry("RiskComplianceAiEvidence", ""),
            Map.entry("ScenarioPlanAiEvidence", ""),
            Map.entry("entryDecisionTechnicalContext", "진입 시점 기술적 사실"),
            Map.entry("entryDecisionOhlcv", "진입 시점 가격·거래량 기록"),
            Map.entry("riskRuleHistoryId", "거래 당시 리스크 규칙 기록"),
            Map.entry("snapshotId", "사전 계획"),
            Map.entry("entryRiskPlan", "진입 당시 리스크 계획"),
            Map.entry("exitRiskPlan", "청산 시점 리스크 계획"),
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

    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) return text;
        String sanitized = replaceWrappedPriceAssignment(text, "손절", "stopLossPrice", "손절가");
        sanitized = replaceWrappedPriceAssignment(sanitized, "익절", "takeProfitPrice", "익절가");
        sanitized = replacePriceAssignment(sanitized, "stopLossPrice", "손절가");
        sanitized = replacePriceAssignment(sanitized, "takeProfitPrice", "익절가");
        sanitized = sanitized
                .replaceAll("(?i)\\bentryRiskPlan\\s*=\\s*none\\b", "진입 당시 별도의 가격 기반 리스크 규칙은 설정되어 있지 않음")
                .replaceAll("(?i)\\bexitRiskPlan\\s*=\\s*none\\b", "청산 시점에 별도의 가격 기반 리스크 규칙은 설정되어 있지 않음")
                .replaceAll("(?i)\\bsnapshot#\\d+", "사전 계획")
                .replaceAll("(?i)\\btradeId\\s*=\\s*\\d+\\s*", "")
                .replaceAll("(?i)\\bcompliance\\s*[:=]?\\s*UNKNOWN\\b", "계획 이행 여부를 명확히 판단할 근거가 부족함")
                .replaceAll("(?i)\\b(?:currentAutoExitEnabled|historicalAutoExitEnabled|autoExitEnabled|autoExit)\\s*=\\s*true\\b", "자동 청산 설정이 활성화됨")
                .replaceAll("(?i)\\b(?:currentAutoExitEnabled|historicalAutoExitEnabled|autoExitEnabled|autoExit)\\s*=\\s*false\\b", "자동 청산 설정이 비활성화됨")
                .replaceAll("(?i)\\bbackend\\s+기술\\s+사실\\b", "당시 기술적 지표")
                .replace("리스크 컴플라이언스", "계획 이행 여부");
        for (Map.Entry<String, String> entry : NATURAL_LANGUAGE) {
            sanitized = sanitized.replace(entry.getKey(), entry.getValue());
        }
        return sanitized
                .replaceAll("(?i)\\bNOT_APPLICABLE\\b", "평가 대상 아님")
                .replaceAll("(?i)\\bUNKNOWN\\b", "근거가 충분하지 않음")
                .replaceAll("(?i)\\bcompliance\\b", "계획 이행 여부")
                .replaceAll("(?i)\\bbackend\\b", "시스템이 계산한")
                .replaceAll("[ \\t]{2,}", " ")
                .strip();
    }

    public static JsonNode sanitizeTextValues(JsonNode node) {
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

    public static JsonNode sanitizedCopy(JsonNode node) {
        return node == null ? null : sanitizeTextValues(node.deepCopy());
    }

    private static String replacePriceAssignment(String text, String field, String label) {
        Pattern pattern = Pattern.compile(Pattern.quote(field) + "\\s*=\\s*([0-9,]+(?:\\.[0-9]+)?)");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = label + " " + formatNumber(matcher.group(1)) + "원";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceWrappedPriceAssignment(String text, String prefix, String field, String label) {
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "\\s*\\(\\s*" + Pattern.quote(field)
                + "\\s*=\\s*([0-9,]+(?:\\.[0-9]+)?)\\s*\\)");
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = label + " " + formatNumber(matcher.group(1)) + "원";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String formatNumber(String raw) {
        BigDecimal value = new BigDecimal(raw.replace(",", ""));
        NumberFormat format = NumberFormat.getNumberInstance(Locale.KOREA);
        format.setGroupingUsed(true);
        format.setMaximumFractionDigits(Math.max(0, value.scale()));
        format.setMinimumFractionDigits(0);
        return format.format(value);
    }
}
