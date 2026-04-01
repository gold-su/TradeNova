package com.tradenova.report.dto;

import java.math.BigDecimal;

public record SessionChartSummary(
        Long chartId,
        Integer chartIndex,
        String symbolTicker,
        String symbolName,
        String status,
        Integer progressIndex,
        int tradeCount,
        int eventCount,
        int snapshotCount,
        boolean traded,
        BigDecimal finalPnL
) {
}
