package com.tradenova.report.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record ReportDocumentResponse(
        Long id,
        Long chartId,
        String kind,          // "DRAFT" or "SNAPSHOT"
        JsonNode contentJson,
        Instant createdAt,
        Instant updatedAt
) {
}
