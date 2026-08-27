package com.tradenova.report.dto;

import com.tradenova.training.analytics.SessionTradeStatistics;

import java.util.List;

/**
 * Backend-calculated facts that an LLM may interpret but must not recalculate.
 */
public record SessionAiDeterministicContext(
        Long sessionId,
        Long userId,
        Long accountId,
        String mode,
        String sessionStatus,
        int totalChartCount,
        int activeChartCount,
        int completedChartCount,
        int tradedChartCount,
        int totalTradeCount,
        SessionTradeStatistics tradeStatistics,
        List<ChartAiDeterministicContext> charts
) {
    public SessionAiDeterministicContext {
        charts = List.copyOf(charts);
    }
}
