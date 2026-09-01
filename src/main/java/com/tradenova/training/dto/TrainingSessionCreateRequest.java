package com.tradenova.training.dto;

import com.tradenova.training.entity.TrainingMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Inputs used to start a training session. */
public record TrainingSessionCreateRequest(
        @NotNull Long accountId,
        @NotNull TrainingMode mode,
        @Min(1) Integer analysisBars,
        @Min(1) Integer trainingBars,
        Integer chartCount,
        @Min(1) Integer bars // legacy total-bars input retained during migration
) {
    /** Compatibility constructor used by the existing frontend and Java callers. */
    public TrainingSessionCreateRequest(
            Long accountId,
            TrainingMode mode,
            Integer bars,
            Integer chartCount
    ) {
        this(accountId, mode, null, null, chartCount, bars);
    }
}
