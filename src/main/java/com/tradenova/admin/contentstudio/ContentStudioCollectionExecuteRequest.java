package com.tradenova.admin.contentstudio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ContentStudioCollectionExecuteRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        String resumeToken,
        @Min(1) @Max(50) Integer maxRequests
) {
}
