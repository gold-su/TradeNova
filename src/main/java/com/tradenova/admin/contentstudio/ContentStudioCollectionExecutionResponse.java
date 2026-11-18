package com.tradenova.admin.contentstudio;

import java.time.LocalDate;
import java.util.List;

public record ContentStudioCollectionExecutionResponse(
        boolean completed,
        String nextResumeToken,
        int requestsMade,
        long savedCandles,
        long existingCandlesSkipped,
        long invalidCandlesSkipped,
        Integer nextSymbolIndex,
        String nextTicker,
        List<PageResult> pages
) {
    public record PageResult(
            String market,
            String ticker,
            LocalDate requestedThrough,
            int attempts,
            int receivedCandles,
            int savedCandles,
            int existingCandlesSkipped,
            int invalidCandlesSkipped
    ) {
    }
}
