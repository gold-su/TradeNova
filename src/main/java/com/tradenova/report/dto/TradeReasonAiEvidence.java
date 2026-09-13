package com.tradenova.report.dto;

import java.time.Instant;

/** A single user-authored statement saved with an actual trade action. */
public record TradeReasonAiEvidence(
        String title,
        String entryReason,
        String riskNote,
        Instant createdAt
) {
}
