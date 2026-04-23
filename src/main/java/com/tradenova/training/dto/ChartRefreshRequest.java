package com.tradenova.training.dto;

/**
 * 차트 새로고침 요청 DTO
 *
 * refreshType:
 * - 어떤 기준으로 새 차트를 뽑을지
 *
 * optionValue:
 * - 세부 옵션 값
 * - RANDOM이면 null 가능
 * - TRAINING_SECTOR면 "SEMICONDUCTOR" 같은 enum 문자열
 * - EXCHANGE_SECTOR면 "은행", "반도체" 같은 문자열
 */
public record ChartRefreshRequest(
        ChartRefreshType refreshType,
        String optionValue
) {
}