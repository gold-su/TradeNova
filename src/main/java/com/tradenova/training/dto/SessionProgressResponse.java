package com.tradenova.training.dto;

import java.math.BigDecimal;

//이번 진행 이후 세션의 현재 상태 요약본
public record SessionProgressResponse(
        Long chartId,
        Integer progressIndex,
        BigDecimal currentPrice,
        String status,

        // 프론트 즉시 반영용 스냅샷
        BigDecimal cashBalance,
        BigDecimal positionQty,
        BigDecimal avgPrice,

        boolean autoExited,              // 이번 진행에서 자동청산 발생 여부
        AutoExitReason reason            // "STOP_LOSS" / "TAKE_PROFIT" / null
) {}
