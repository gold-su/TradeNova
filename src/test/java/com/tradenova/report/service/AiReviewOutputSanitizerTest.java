package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiReviewOutputSanitizerTest {

    @Test
    void convertsDeveloperRiskTermsToNaturalKorean() {
        String sanitized = AiReviewOutputSanitizer.sanitize(
                "structured stop loss와 take profit, auto risk rule을 확인했습니다");

        assertFalse(sanitized.contains("structured stop loss"));
        assertFalse(sanitized.contains("take profit"));
        assertFalse(sanitized.contains("auto risk rule"));
        assertTrue(sanitized.contains("사전에 설정한 손절 조건"));
        assertTrue(sanitized.contains("익절 조건"));
        assertTrue(sanitized.contains("자동 청산 조건"));
    }

    @Test
    void removesInternalTokensAndUnsupportedMarkdown() throws Exception {
        String input = "**stopLossPrice가 없음**; exitRiskPlan; Backend Risk Plan Compliance=FOLLOWED; "
                + "snapshot#1; SNAPSHOT#123; tradeId=260 매수; tradeId=999 매도; "
                + "stopLoss; takeProfit; autoExit; compliance UNKNOWN; UNKNOWN; "
                + "ACTION-TIME TRADE REASON; currentAutoExitEnabled=false; COMPLETED; "
                + "TradeReasonAiEvidence; entryRiskPlan=none; NOT_APPLICABLE; backend 기술 사실; 리스크 컴플라이언스; "
                + "entryDecisionTechnicalContext; entryDecisionOhlcv; riskRuleHistoryId; snapshotId; "
                + "NOT_FOLLOWED; STOP_LOSS; TAKE_PROFIT; END_OF_CHART; END_OF_SESSION; "
                + "NO_REASON; OBSERVATION_ONLY; CONDITIONED; TERMINAL_FORCED_EXIT";

        String sanitized = AiReviewOutputSanitizer.sanitize(input);

        assertTrue(sanitized.contains("손절가가 없음"));
        assertFalse(sanitized.contains("**"));
        assertTrue(sanitized.contains("청산 시점 리스크 계획"));
        assertTrue(sanitized.contains("계획 실행 일치 여부=사전에 정한 기준을 따름"));
        assertTrue(sanitized.contains("사전 계획"));
        assertTrue(sanitized.contains("매수; 매도"));
        assertTrue(sanitized.contains("손절 기준; 익절 기준; 자동 청산 설정"));
        assertTrue(sanitized.contains("계획 이행 여부를 명확히 판단할 근거가 부족함"));
        assertTrue(sanitized.contains("거래 당시 기록한 이유"));
        assertTrue(sanitized.contains("자동 청산 설정이 비활성화됨"));
        assertTrue(sanitized.contains("진입 당시 별도의 가격 기반 리스크 규칙은 설정되어 있지 않음"));
        assertTrue(sanitized.contains("평가 대상 아님"));
        assertTrue(sanitized.contains("당시 기술적 지표"), sanitized);
        assertFalse(sanitized.contains("stopLossPrice"));
        assertFalse(sanitized.contains("stopLoss"));
        assertFalse(sanitized.contains("takeProfit"));
        assertFalse(sanitized.contains("autoExit"));
        assertFalse(sanitized.contains("tradeId"));
        assertFalse(sanitized.contains("UNKNOWN"));
        assertFalse(sanitized.contains("FOLLOWED"));
        assertFalse(sanitized.contains("TradeReasonAiEvidence"));
        assertFalse(sanitized.contains("entryRiskPlan"));
        assertFalse(sanitized.contains("NOT_APPLICABLE"));
        assertFalse(sanitized.toLowerCase().contains("backend"));
        assertFalse(sanitized.toLowerCase().contains("compliance"));
        assertFalse(sanitized.toLowerCase().contains("snapshot#"));
        assertFalse(sanitized.contains("entryDecisionTechnicalContext"));
        assertFalse(sanitized.contains("entryDecisionOhlcv"));
        assertFalse(sanitized.contains("riskRuleHistoryId"));
        assertFalse(sanitized.contains("snapshotId"));
        assertFalse(sanitized.contains("NOT_FOLLOWED"));
        assertFalse(sanitized.contains("STOP_LOSS"));
        assertFalse(sanitized.contains("TAKE_PROFIT"));
        assertFalse(sanitized.contains("END_OF_CHART"));
        assertFalse(sanitized.contains("END_OF_SESSION"));
        assertFalse(sanitized.contains("NO_REASON"));
        assertFalse(sanitized.contains("OBSERVATION_ONLY"));
        assertFalse(sanitized.contains("CONDITIONED"));
        assertFalse(sanitized.contains("TERMINAL_FORCED_EXIT"));
    }

    @Test
    void softensKnownImperativeEndingsWithoutBroadSentenceRewriting() {
        String sanitized = AiReviewOutputSanitizer.sanitize(
                "다음에는 이유를 기록하라. 조건을 확인하라. 습관을 들여라."
        );

        assertEquals("다음에는 이유를 기록해 보세요. 조건을 확인해 보세요. 습관을 들여 보세요.", sanitized);
    }

    @Test
    void removesInternalLinkAndTaxonomyLanguage() {
        String sanitized = AiReviewOutputSanitizer.sanitize(
                "canonical exact link가 있고 계획 선택 관계가 저장됨. PLAN_EXECUTION_PARTIAL_DEVIATION, MISMATCH"
        );

        assertFalse(sanitized.contains("exact link"));
        assertFalse(sanitized.contains("계획 선택 관계가 저장됨"));
        assertFalse(sanitized.contains("PLAN_EXECUTION"));
        assertFalse(sanitized.contains("MISMATCH"));
        assertTrue(sanitized.contains("직접 연결된 기록이 있고"));
        assertTrue(sanitized.contains("계획과 실제 행동의 일부 차이"));
    }

    @Test
    void naturalizesKnownPriceAssignmentsAndPreservesValues() {
        String sanitized = AiReviewOutputSanitizer.sanitize(
                "손절(stopLossPrice=46455), 익절(takeProfitPrice=58680)"
        );

        assertTrue(sanitized.contains("손절가 46,455원"));
        assertTrue(sanitized.contains("익절가 58,680원"));
        assertFalse(sanitized.contains("stopLossPrice"));
        assertFalse(sanitized.contains("takeProfitPrice"));
    }

    @Test
    void sanitizesNestedSessionTextWithoutChangingJsonFieldNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("""
                {"riskReview":{"assessment":"exitRiskPlan은 FOLLOWED", "evidence":["**autoExitEnabled=true**"]}}
                """);

        JsonNode sanitized = AiReviewOutputSanitizer.sanitizeTextValues(node);

        assertTrue(sanitized.has("riskReview"));
        assertTrue(sanitized.path("riskReview").path("assessment").asText().contains("청산 시점 리스크 계획"));
        assertTrue(sanitized.path("riskReview").path("evidence").get(0).asText()
                .contains("자동 청산 설정이 활성화됨"));
        assertFalse(sanitized.path("riskReview").path("evidence").get(0).asText().contains("**"));
    }

    @Test
    void removesKoreanBackendAndEnglishEvidenceFromActualQaWording() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree("""
                {
                  "summary":"구체적 evidence가 부족함",
                  "riskReview":{
                    "assessment":"백엔드 계획 이행 여부는 UNKNOWN으로, 명확한 리스크 트리거가 없음",
                    "evidence":["백엔드 기술 사실이 판단을 일부 뒷받침"]
                  }
                }
                """);

        JsonNode sanitized = AiReviewOutputSanitizer.sanitizeTextValues(node);
        String userFacingText = sanitized.path("summary").asText()
                + sanitized.path("riskReview").path("assessment").asText()
                + sanitized.path("riskReview").path("evidence").get(0).asText();

        assertFalse(userFacingText.contains("백엔드"));
        assertFalse(userFacingText.contains("UNKNOWN"));
        assertFalse(userFacingText.toLowerCase().contains("evidence"));
        assertTrue(sanitized.path("summary").asText().contains("구체적 근거"));
        assertTrue(sanitized.path("riskReview").path("assessment").asText()
                .contains("사전 계획 이행 여부를 명확히 확인할 수 있는 기록이 부족하여"));
        assertTrue(sanitized.path("riskReview").path("evidence").get(0).asText()
                .contains("당시 기술적 지표"));
    }
}
