package com.tradenova.report.dto;

import java.util.List;

public record ChartQualitativeEvidenceContext(
        Long chartId,
        boolean active,
        boolean refreshed,
        List<SnapshotAiEvidence> snapshots,
        List<NoteAiEvidence> notes
) {
    public ChartQualitativeEvidenceContext {
        snapshots = List.copyOf(snapshots);
        notes = List.copyOf(notes);
    }
}
