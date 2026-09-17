package com.tradenova.training.dto;

import com.tradenova.report.dto.TrainingEventResponse;
import java.util.List;

public record TrainingHistoryDetailResponse(
        TrainingHistorySummaryResponse session,
        TrainingEventResponse sessionAiReview,
        List<TrainingHistoryChartResponse> charts
) {
}
