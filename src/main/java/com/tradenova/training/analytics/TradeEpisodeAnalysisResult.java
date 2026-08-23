package com.tradenova.training.analytics;

import java.util.List;

public record TradeEpisodeAnalysisResult(Long chartId, List<TradeEpisode> episodes) {
    public TradeEpisodeAnalysisResult {
        episodes = List.copyOf(episodes);
    }
}
