package com.tradenova.training.dto;

import com.tradenova.training.entity.TradeSide;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 훈련 거래 내역 단건 응답 DTO
 *
 * 역할:
 * - 특정 매수/매도 거래 정보를 프론트에 전달
 * - 차트별 거래 히스토리 조회 시 사용
 *
 * 포함 정보:
 * - 어떤 거래인지 (tradeId)
 * - 어떤 차트/계좌/종목인지
 * - 매수(BUY) / 매도(SELL) 여부
 * - 거래 가격 및 수량
 * - 거래 발생 시간
 *
 * 사용 예시:
 * - 거래 내역 패널
 * - 차트 buy/sell 마커
 * - 거래 로그 목록
 */
public record TrainingTradeItemResponse(
        // 거래 ID
        Long tradeId,
        // 거래 발생 차트 ID
        Long chartId,
        // 거래 계좌 ID
        Long accountId,
        // 거래 종목 ID
        Long symbolId,
        // 거래 방향 (BUY/SELL)
        TradeSide side,
        // 체결 가격
        BigDecimal price,
        // 거래 수량
        BigDecimal qty,
        // 어떤 캔들 거래인지
        Long candleTime,
        // 거래 발생 시간
        OffsetDateTime createdAt
) {
}