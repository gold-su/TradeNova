package com.tradenova.training.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic session statistics recalculated from canonical trade episodes.
 * winRate is a probability in [0, 1], not a percentage. Undefined averages and
 * ratios are null; totalRealizedPnl remains defined for an empty session as zero.
 */
public record SessionTradeStatistics(
        int totalEpisodeCount,
        int closedEpisodeCount,
        int openEpisodeCount,
        int winningClosedEpisodeCount,
        int losingClosedEpisodeCount,
        int breakevenClosedEpisodeCount,
        int holdingBarsSampleCount,
        BigDecimal winRate,
        BigDecimal averageWinningRealizedPnl,
        BigDecimal averageLosingRealizedPnl,
        BigDecimal payoffRatio,
        BigDecimal expectancy,
        BigDecimal totalRealizedPnl,
        BigDecimal averageReturnPct,
        BigDecimal averageHoldingBars,
        BigDecimal averageEntryCount,
        BigDecimal averageExitCount,
        List<TradeEpisodeReference> winningEpisodes,
        List<TradeEpisodeReference> losingEpisodes,
        List<TradeEpisodeReference> breakevenEpisodes,
        List<TradeEpisodeReference> openEpisodes
) {
    public SessionTradeStatistics {
        winningEpisodes = List.copyOf(winningEpisodes);
        losingEpisodes = List.copyOf(losingEpisodes);
        breakevenEpisodes = List.copyOf(breakevenEpisodes);
        openEpisodes = List.copyOf(openEpisodes);
    }
}
