package com.tradenova.report.dto;

/** Deterministic Chart AI lifecycle facts; current state and historical execution stay separate. */
public record ChartLifecycleAiEvidence(
        String chartStatus,
        boolean historicalBuyExecuted,
        Long latestBuyTradeId,
        Long latestTradeId,
        String latestTradeSide,
        boolean finalPositionClosed,
        String terminalLiquidationReason,
        Long terminalLiquidationTradeId,
        Long entryRiskRuleHistoryId,
        Boolean historicalAutoExitEnabled,
        Boolean currentAutoExitEnabled
) {
}
