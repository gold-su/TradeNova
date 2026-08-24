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
        List<SessionSnapshotSummary> snapshots,
        SessionAiDeterministicContext deterministicContext,
        SessionQualitativeEvidenceContext qualitativeEvidenceContext
) {
    public SessionAiAnalysisRequest(
            Long sessionId, Long accountId, String mode, String sessionStatus,
            int totalChartCount, int completedChartCount, int totalTradeCount, int totalEventCount,
            List<SessionChartSummary> charts, List<SessionSnapshotSummary> snapshots,
            SessionAiDeterministicContext deterministicContext
    ) {
        this(sessionId, accountId, mode, sessionStatus, totalChartCount, completedChartCount,
                totalTradeCount, totalEventCount, charts, snapshots, deterministicContext, null);
    }
    /**
     * Backward-compatible constructor for callers that have not adopted deterministic context yet.
     */
    public SessionAiAnalysisRequest(
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
        this(
                sessionId, accountId, mode, sessionStatus,
                totalChartCount, completedChartCount, totalTradeCount, totalEventCount,
                charts, snapshots, null, null
        );
    }
}
