package com.tradenova.report.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ReportDraftUpsertRequest(
        @NotNull JsonNode payloadJson
) {
}
