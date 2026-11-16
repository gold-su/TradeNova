package com.tradenova.admin.contentstudio;

import java.time.LocalDate;
import java.util.List;

public record ContentStudioCollectionPlanResponse(
        LocalDate fromDate,
        LocalDate toDate,
        int targetSymbolCount,
        int existingSymbolCount,
        int plannedSymbolInsertCount,
        long estimatedWeekdayUpperBoundPerSymbol,
        long estimatedApiRequestUpperBound,
        long estimatedNewCandleUpperBound,
        List<SymbolPlan> symbols
) {
    public record SymbolPlan(
            String market,
            String ticker,
            String name,
            boolean symbolExists,
            long existingCandleCount,
            LocalDate firstStoredDate,
            LocalDate lastStoredDate,
            long estimatedApiRequestUpperBound,
            long estimatedNewCandleUpperBound
    ) {
    }
}
