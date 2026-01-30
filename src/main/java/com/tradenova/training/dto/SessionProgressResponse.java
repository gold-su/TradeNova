package com.tradenova.training.dto;

import java.math.BigDecimal;

//이번 진행 이후 세션의 현재 상태 요약본
public record SessionProgressResponse(
        Long sessionId,
        Integer progressIndex,
        BigDecimal currentPrice,
        String status,
        boolean autoExited,              // 이번 진행에서 자동청산 발생 여부
        AutoExitReason reason            // "STOP_LOSS" / "TAKE_PROFIT" / null
) {}
