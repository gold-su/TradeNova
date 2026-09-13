package com.tradenova.report.dto;

import java.util.List;

public record ChartQualitativeEvidenceContext(
        Long chartId,
        boolean active,
        boolean refreshed,
        List<SnapshotAiEvidence> snapshots,
        List<NoteAiEvidence> notes,
        List<TradeActionAiEvidence> tradeActions
) {
    public ChartQualitativeEvidenceContext {
        snapshots = List.copyOf(snapshots);
        notes = List.copyOf(notes);
        tradeActions = List.copyOf(tradeActions);
    }

    public ChartQualitativeEvidenceContext(Long chartId, boolean active, boolean refreshed,
                                           List<SnapshotAiEvidence> snapshots, List<NoteAiEvidence> notes) {
        this(chartId, active, refreshed, snapshots, notes, List.of());
    }
}
