package com.tradenova.training.dto;

import com.tradenova.training.entity.TrainingChartStatus;

import java.time.LocalDate;

public record ChartSummaryResponse(
        Long chartId,
        Integer chartIndex,
        Long symbolId,
        String symbolTicker,
        String symbolName,
        Integer bars,
        Integer progressIndex,
        TrainingChartStatus status,
        LocalDate startDate,
        LocalDate endDate
) {
}
