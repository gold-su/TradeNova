package com.tradenova.training.dto;

import java.math.BigDecimal;

public record RiskRuleUpsertRequest(
        BigDecimal stopLossPrice,   // 손절가
        BigDecimal takeProfitPrice, // 익절가
        Boolean autoExitEnabled     // 자동매도 ON/OFF
) {
}
