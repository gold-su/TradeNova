package com.tradenova.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuickPhraseUpdateRequest(
        @NotNull @Size(max=40) String title,
        @NotBlank @Size(max=2000) String content
) {
}
