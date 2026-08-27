package com.tradenova.training.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * A deterministic, recalculable long-position episode derived from trade rows.
 */
public record TradeEpisode(
        int episodeIndex,
        Long chartId,
        Long accountId,
        Long symbolId,
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
    public TradeEpisode {
        entryTradeIds = List.copyOf(entryTradeIds);
        exitTradeIds = List.copyOf(exitTradeIds);
        allTradeIds = List.copyOf(allTradeIds);
    }
}
