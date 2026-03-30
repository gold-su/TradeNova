package com.tradenova.training.dto;

public record SessionFinishResponse(
        Long sessionId,
        String sessionStatus,
        int totalChartCount,
        int completedChartCount,
        int forceCompletedChartCount
) {
}
