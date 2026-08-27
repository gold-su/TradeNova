package com.tradenova.report.dto;

import com.tradenova.training.analytics.TradeEpisodeReference;

/** Explicit, non-inferred links available for a qualitative evidence item. */
public record EvidenceTimelineAnchor(
        Integer progressIndex,
        Long candleTime,
        Long tradeId,
        TradeEpisodeReference episodeReference,
        Long riskRuleHistoryId,
        String resolution
) {
}
