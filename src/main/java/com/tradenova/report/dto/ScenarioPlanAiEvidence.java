package com.tradenova.report.dto;

import java.time.Instant;

/** Explicitly linked, user-authored SCENARIO snapshot. Never inferred by time or recency. */
public record ScenarioPlanAiEvidence(Long snapshotId, Long chartId, Integer version, Instant authoredAt,
                                     String thesis, String entryReason, String exitPlan, String riskNote,
                                     String freeNote) { }
