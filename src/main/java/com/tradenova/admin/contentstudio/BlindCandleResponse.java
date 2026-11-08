package com.tradenova.admin.contentstudio;

public record BlindCandleResponse(
        int idx,
        double o,
        double h,
        double l,
        double c,
        long v
) {
}
