package com.tradenova.report.dto;

import java.time.Instant;

public record SnapshotAiEvidence(
        Long snapshotId,
        Integer version,
        Instant authoredAt,
        Long linkedEventId,
        String thesis,
        String entryReason,
        String exitPlan,
        String riskNote,
        String freeNote,
        EvidenceTimelineAnchor timeline
) {
}
