package com.tradenova.admin.contentstudio;

import java.util.List;

public record BlindContentCaseResponse(
        String caseKey,
        int analysisBars,
        int futureBars,
        int visibleThroughIndex,
        int remainingBars,
        boolean atEnd,
        List<BlindCandleResponse> candles
) {
}
