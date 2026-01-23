package com.tradenova.training.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// 얼마를 사고/팔 것인지 수량(qty)만 서버에 전달하는 DTO
public record TradeRequest(
        @NotNull
        @DecimalMin(value = "0.000001")
        BigDecimal qty
) {
}
