package com.tradenova.training.dto;

import com.tradenova.training.entity.TrainingMode;
import com.tradenova.training.entity.TrainingStatus;

import java.util.List;

/**
 * 현재 진행 중신 세션 정보 전달용 DTO
 */
public record ActiveTrainingSessionResponse(
        Long sessionId,
        Long accountId,
        TrainingMode mode,
        TrainingStatus status,
        int totalChartCount,
        int completedChartCount,
        List<ChartSummaryResponse> charts
) {
}
