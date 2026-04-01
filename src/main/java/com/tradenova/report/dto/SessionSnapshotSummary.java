package com.tradenova.report.dto;

public record SessionSnapshotSummary(
        Long chartId,
        Integer version,
        String thesis,
        String entryReason,
        String exitPlan,
        String riskNote,
        String freeNote
) {
}
