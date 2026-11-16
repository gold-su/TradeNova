package com.tradenova.admin.contentstudio;

import java.util.List;

public record ContentCaseAnalysisResponse(
        ContentCaseType primaryType,
        List<ContentCaseType> matchedTypes,
        double decisionClose,
        Double closeReturn5dPct,
        Double closeReturn10dPct,
        Double closeReturn20dPct,
        double futureMaxHighReturnPct,
        double futureMinLowReturnPct,
        double futureFinalCloseReturnPct
) {
}
