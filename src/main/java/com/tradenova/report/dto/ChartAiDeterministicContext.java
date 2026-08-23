package com.tradenova.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Stable chart facts and calculated trade evidence supplied to a future AI request.
 */
public record ChartAiDeterministicContext(
        Long chartId,
        Integer chartIndex,
        Long symbolId,
        String symbolTicker,
        String symbolName,
        String trainingSector,
        LocalDate startDate,
        LocalDate endDate,
        Integer bars,
        Integer progressIndex,
        String status,
        boolean active,
        boolean refreshed,
        int tradeCount,
        int episodeCount,
        boolean traded,
        List<TradeEpisodeAiContext> episodes
) {
    public ChartAiDeterministicContext {
        episodes = List.copyOf(episodes);
    }
}
