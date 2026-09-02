package com.tradenova.training.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RiskRuleResponse(
        Long id,
        Long chartId,
        Long accountId,
        BigDecimal stopLossPrice,
        Integer stopLossExitPercent,
        BigDecimal takeProfitPrice,
        Integer takeProfitExitPercent,
        Boolean autoExitEnabled,
        OffsetDateTime updatedAt
) {
}
