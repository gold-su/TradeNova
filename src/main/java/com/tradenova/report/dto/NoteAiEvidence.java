package com.tradenova.report.dto;

import java.time.Instant;

public record NoteAiEvidence(
        Long eventId,
        Instant authoredAt,
        String summary,
        String detail,
        EvidenceTimelineAnchor timeline
) {
}
