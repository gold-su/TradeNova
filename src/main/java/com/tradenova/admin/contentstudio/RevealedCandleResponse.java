package com.tradenova.admin.contentstudio;

import java.time.LocalDate;

public record RevealedCandleResponse(
        int idx,
        LocalDate date,
        double o,
        double h,
        double l,
        double c,
        long v
) {
}
