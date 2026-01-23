package com.tradenova.training.dto;

import java.math.BigDecimal;

// 이번 매매가 끝난 직후, 세션의 핵심 상태를 프론트에 알려주는 요약 응답
public record TradeResponse(
        Long sessionId, // 어떤 세션에서 발생한 거래인지
        Long tradeId,   // 방금 생성된 TrainingTrade의 PK
        BigDecimal cashBalance, // 거래 후 남은 현금
        BigDecimal positionQty, // 현재 보유 수량(거래 후)
        BigDecimal avgPrice,    // 현재 포지션의 평단가
        BigDecimal executedPrice// 이번 거래가 실제로 체결된 가격
) {
}
