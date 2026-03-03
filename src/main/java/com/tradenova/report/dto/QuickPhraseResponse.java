package com.tradenova.report.dto;

public record QuickPhraseResponse(
        Long id,
        String title,
        String content,
        Integer sortOrder
) {
}
