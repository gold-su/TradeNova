package com.tradenova.admin.contentstudio;

import java.time.LocalDate;
import java.util.List;

public record ContentCaseRevealResponse(
        String caseKey,
        Long symbolId,
        String market,
        String ticker,
        String name,
        String currency,
        LocalDate startDate,
        LocalDate blindThroughDate,
        LocalDate endDate,
        int analysisBars,
        int futureBars,
        List<RevealedCandleResponse> candles
) {
}
