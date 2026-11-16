package com.tradenova.admin.contentstudio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ContentStudioRandomRequest(
        Long seed,
        @Min(20) @Max(240) Integer analysisBars,
        @Min(1) @Max(120) Integer futureBars
) {
}
