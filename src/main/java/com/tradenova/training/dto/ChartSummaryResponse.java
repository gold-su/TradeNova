package com.tradenova.training.dto;

import com.tradenova.training.entity.TrainingChartStatus;

import java.time.LocalDate;

public record ChartSummaryResponse(
        Long chartId,
        Integer chartIndex,
        Long symbolId,
        String symbolTicker,
        String symbolName,
        String trainingSector,
        Integer bars,
        Integer analysisBars,
        Integer trainingBars,
        Integer progressIndex,
        TrainingChartStatus status,
        LocalDate startDate,
        LocalDate endDate
) {
}