package com.tradenova.report.dto;

import java.util.List;

public record SessionAiAnalysisRequest(
        Long sessionId,
        Long accountId,
        String mode,
        String sessionStatus,
        int totalChartCount,
        int completedChartCount,
        int totalTradeCount,
        int totalEventCount,
        List<SessionChartSummary> charts,
        List<SessionSnapshotSummary> snapshots
) {
}
