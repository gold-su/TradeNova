package com.tradenova.training.dto;

import java.time.LocalDate;

public record ChartSummaryResponse(
        Long chartId,
        Integer chartIndex,
        Long symbolId,
        String symbolTicker,
        String symbolName,
        Integer bars,
        Integer progressIndex,
        LocalDate startDate,
        LocalDate endDate
) {
}
