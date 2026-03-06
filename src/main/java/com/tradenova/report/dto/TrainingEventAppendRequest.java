package com.tradenova.report.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.report.entity.Type;

public record TrainingEventAppendRequest(
        Type type,              // NOTE/WARNING 등 (없으면 NOTE로 처리)
        String title,           // 한 줄 로그
        JsonNode payloadJson    // 상세 JSON
) {
}
