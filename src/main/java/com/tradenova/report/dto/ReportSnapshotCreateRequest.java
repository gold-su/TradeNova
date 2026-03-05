package com.tradenova.report.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ReportSnapshotCreateRequest(
        Long linkedEventId,          // 선택(optional)
        @NotNull JsonNode payloadJson // 필수
) {
}
