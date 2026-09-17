package com.tradenova.training.dto;

import java.time.Instant;
import java.time.OffsetDateTime;

/** Counts include all persisted chart attempts, including refreshed historical charts. */
public record TrainingHistorySummaryResponse(
        Long sessionId,
        String status,
        OffsetDateTime createdAt,
        Instant completedAt,
        int totalChartCount,
        int completedChartCount,
        long totalTradeCount,
        long snapshotCount,
        boolean hasSessionAiReview,
        Integer sessionAiScore
) {
}
