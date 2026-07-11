package com.tradenova.training.dto;

/**
 * 훈련 완료 화면에 필요한 세션 요약 응답
 *
 * @param sessionId 세션 ID
 * @param status 세션 상태
 * @param totalChartCount 전체 활성 차트 수
 * @param completedChartCount 완료된 활성 차트 수
 * @param tradeCount 세션에서 발생한 거래 횟수
 * @param snapshotCount 세션에서 생성된 스냅샷 수
 * @param sessionAiExists 세션 AI 분석 존재 여부
 * @param sessionAiScore 세션 AI 점수. 분석이 없으면 null
 */
public record SessionSummaryResponse(
        Long sessionId,
        String status,
        int totalChartCount,
        int completedChartCount,
        long tradeCount,
        long snapshotCount,
        boolean sessionAiExists,
        Integer sessionAiScore
) {
}