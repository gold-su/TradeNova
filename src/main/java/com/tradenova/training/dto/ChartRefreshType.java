package com.tradenova.training.dto;

/**
 * 차트 새로고침 방식
 *
 * RANDOM:
 * - 전체 활성 종목 중 랜덤
 *
 * TRAINING_SECTOR:
 * - TradeNova 내부 훈련 섹터 기준 랜덤
 *
 * EXCHANGE_SECTOR:
 * - 거래소 업종 문자열 기준 랜덤
 *
 * 아래 값들은 지금 바로 구현하지 않아도 되고,
 * 구조를 미리 열어두기 위한 확장 포인트다.
 */
public enum ChartRefreshType {
    RANDOM,
    TRAINING_SECTOR,
    EXCHANGE_SECTOR,

    // ===== 향후 확장 =====
    TOP_VOLUME,
    ORDER_FLOW,
    THEME
}