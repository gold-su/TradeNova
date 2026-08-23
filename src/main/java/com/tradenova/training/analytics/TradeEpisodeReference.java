package com.tradenova.training.analytics;

/**
 * Session-wide episode identifier. Episode indexes are only unique within a chart.
 */
public record TradeEpisodeReference(Long chartId, int episodeIndex) {
}
