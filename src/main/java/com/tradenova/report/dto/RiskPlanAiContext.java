package com.tradenova.report.dto;

import java.math.BigDecimal;

/**
 * Immutable risk-plan snapshot evidence resolved from TrainingRiskRuleHistory.
 */
public record RiskPlanAiContext(
        Long riskRuleHistoryId,
        BigDecimal stopLossPrice,
        BigDecimal takeProfitPrice,
        boolean autoExitEnabled,
        Integer progressIndex,
        Long candleTime
) {
}
