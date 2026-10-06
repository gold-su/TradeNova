package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiReviewOutputSanitizerTest {

    @Test
    void removesInternalTokensAndPreservesBoldMarkers() throws Exception {
        String input = "**stopLossPrice가 없음**; exitRiskPlan; Backend Risk Plan Compliance=FOLLOWED; "
                + "snapshot#1; SNAPSHOT#123; tradeId=260 매수; tradeId=999 매도; "
                + "stopLoss; takeProfit; autoExit; compliance UNKNOWN; UNKNOWN; "
                + "ACTION-TIME TRADE REASON; currentAutoExitEnabled=false; COMPLETED";

        String sanitized = AiReviewOutputSanitizer.sanitize(input);

        assertTrue(sanitized.contains("**손절가가 없음**"));
        assertTrue(sanitized.contains("청산 계획"));
        assertTrue(sanitized.contains("계획 실행 일치 여부=사전에 정한 기준을 따름"));
        assertTrue(sanitized.contains("사전 계획"));
        assertTrue(sanitized.contains("매수; 매도"));
        assertTrue(sanitized.contains("손절 기준; 익절 기준; 자동 청산 설정"));
        assertTrue(sanitized.contains("계획 이행 여부를 명확히 판단할 근거가 부족함"));
        assertTrue(sanitized.contains("거래 당시 기록한 이유"));
        assertTrue(sanitized.contains("현재 자동 청산 설정=false"));
        assertFalse(sanitized.contains("stopLossPrice"));
        assertFalse(sanitized.contains("stopLoss"));
        assertFalse(sanitized.contains("takeProfit"));
        assertFalse(sanitized.contains("autoExit"));
        assertFalse(sanitized.contains("tradeId"));
        assertFalse(sanitized.contains("UNKNOWN"));
        assertFalse(sanitized.contains("FOLLOWED"));
        assertFalse(sanitized.toLowerCase().contains("snapshot#"));
    }

    @Test
    void sanitizesNestedSessionTextWithoutChangingJsonFieldNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("""
                {"riskReview":{"assessment":"exitRiskPlan은 FOLLOWED", "evidence":["**autoExitEnabled=true**"]}}
                """);

        JsonNode sanitized = AiReviewOutputSanitizer.sanitizeTextValues(node);

        assertTrue(sanitized.has("riskReview"));
        assertTrue(sanitized.path("riskReview").path("assessment").asText().contains("청산 계획"));
        assertTrue(sanitized.path("riskReview").path("evidence").get(0).asText()
                .contains("**자동 청산 설정=true**"));
    }
}
