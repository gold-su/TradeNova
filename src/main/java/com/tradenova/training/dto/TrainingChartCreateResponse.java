package com.tradenova.training.dto;

import java.time.LocalDate;

public record TrainingChartCreateResponse (
        Long chartId,
        Integer chartIndex,
        Long symbolId,
        String symbolTicker,
        String symbolName,
        Integer bars,
        Integer progressIndex,
        LocalDate startDate,
        LocalDate endDate
){
}
