package com.tradenova.market.repository;

import java.time.LocalDate;

public record MarketCandleCoverage(
        Long symbolId,
        long candleCount,
        LocalDate firstDate,
        LocalDate lastDate
) {
}
