package com.tradenova.report.dto;

import java.time.Instant;

/** The exact Scenario snapshot explicitly selected for a trade action. */
public record ScenarioPlanAiEvidence(
        Long snapshotId,
        Long chartId,
        Integer version,
        Instant authoredAt,
        String thesis,
        String entryReason,
        String exitPlan,
        String riskNote,
        String freeNote
) {
}
