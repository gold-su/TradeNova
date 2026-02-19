package com.tradenova.training.dto;

import com.tradenova.training.entity.TrainingMode;
import com.tradenova.training.entity.TrainingStatus;

public record SessionDetailResponse(
        Long sessionId,
        Long accountId,
        TrainingMode mode,
        TrainingStatus status
) {
}
