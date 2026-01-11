package com.tradenova.training.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RiskRuleResponse(
        Long id,
        Long sessionId,
        Long accountId,
        BigDecimal stopLossPrice,
        BigDecimal takeProfitPrice,
        Boolean autoExitEnabled,
        OffsetDateTime updatedAt
) {
}
