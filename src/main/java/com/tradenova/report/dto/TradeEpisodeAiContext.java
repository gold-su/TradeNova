package com.tradenova.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic episode evidence for AI interpretation; contains no JPA entities.
 */
public record TradeEpisodeAiContext(
        int episodeIndex,
        List<Long> entryTradeIds,
        List<Long> exitTradeIds,
        List<Long> allTradeIds,
        Long openedAtCandleTime,
        Long closedAtCandleTime,
        int entryCount,
        int exitCount,
        BigDecimal totalEntryQty,
        BigDecimal totalExitQty,
        BigDecimal weightedEntryPrice,
        BigDecimal weightedExitPrice,
        BigDecimal realizedPnl,
        BigDecimal returnPct,
        Integer holdingBars,
        boolean closed,
        BigDecimal remainingQty,
        Long firstEntryRiskRuleHistoryId,
        Long lastExitRiskRuleHistoryId
) {
    public TradeEpisodeAiContext {
        entryTradeIds = List.copyOf(entryTradeIds);
        exitTradeIds = List.copyOf(exitTradeIds);
        allTradeIds = List.copyOf(allTradeIds);
    }
}
