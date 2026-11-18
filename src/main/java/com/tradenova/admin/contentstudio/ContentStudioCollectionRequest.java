package com.tradenova.admin.contentstudio;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ContentStudioCollectionRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate
) {
}
