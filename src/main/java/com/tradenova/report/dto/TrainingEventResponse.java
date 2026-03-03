package com.tradenova.report.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record TrainingEventResponse(
        Long id,
        Long chartId,
        String type,          // Type enum name
        String title,         // 한줄 로그
        JsonNode payloadJson,
        Instant createdAt
) {
}
