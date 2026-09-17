package com.tradenova.training.dto;

import com.tradenova.report.dto.TrainingEventResponse;

public record TrainingHistoryChartResponse(
        Long chartId,
        Integer chartIndex,
        String status,
        boolean active,
        boolean refreshed,
        String symbolTicker,
        String symbolName,
        String sector,
        long tradeCount,
        long snapshotCount,
        boolean hasChartAiReview,
        Integer chartAiScore,
        TrainingEventResponse chartAiReview
) {
}
