package com.tradenova.training.analytics;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure session-level aggregation over deterministic chart episode results.
 */
@Component
public class SessionTradeStatisticsCalculator {

    private static final int SCALE = 8;

    public SessionTradeStatistics calculate(List<TradeEpisodeAnalysisResult> chartResults) {
        List<TradeEpisode> episodes = new ArrayList<>();
        Set<TradeEpisodeReference> seenReferences = new HashSet<>();

        for (TradeEpisodeAnalysisResult chartResult : chartResults) {
            if (chartResult == null || chartResult.chartId() == null) {
                throw new TradeEpisodeDataException("Every chart episode result requires a chart id");
            }
            for (TradeEpisode episode : chartResult.episodes()) {
                validateEpisode(chartResult.chartId(), episode);
                TradeEpisodeReference reference = reference(episode);
                if (!seenReferences.add(reference)) {
                    throw new TradeEpisodeDataException("Duplicate episode reference: " + reference);
                }
                episodes.add(episode);
            }
        }

        List<TradeEpisode> closed = episodes.stream().filter(TradeEpisode::closed).toList();
        List<TradeEpisode> open = episodes.stream().filter(episode -> !episode.closed()).toList();
        List<TradeEpisode> wins = closed.stream().filter(episode -> episode.realizedPnl().signum() > 0).toList();
        List<TradeEpisode> losses = closed.stream().filter(episode -> episode.realizedPnl().signum() < 0).toList();
        List<TradeEpisode> breakeven = closed.stream().filter(episode -> episode.realizedPnl().signum() == 0).toList();
        List<TradeEpisode> holdingBarsSamples = closed.stream()
                .filter(episode -> episode.holdingBars() != null)
                .toList();

        BigDecimal closedCount = BigDecimal.valueOf(closed.size());
        BigDecimal totalRealizedPnl = sum(episodes, TradeEpisode::realizedPnl);
        BigDecimal closedRealizedPnl = sum(closed, TradeEpisode::realizedPnl);
        BigDecimal averageWin = average(wins, TradeEpisode::realizedPnl);
        BigDecimal averageLoss = average(losses, TradeEpisode::realizedPnl);

        // Expectancy is the direct mean of closed PnL. This is equivalent to
        // P(win)*avgWin + P(loss)*avgLoss because breakeven contributes zero.
        return new SessionTradeStatistics(
                episodes.size(),
                closed.size(),
                open.size(),
                wins.size(),
                losses.size(),
                breakeven.size(),
                holdingBarsSamples.size(),
                closed.isEmpty() ? null : divide(BigDecimal.valueOf(wins.size()), closedCount),
                averageWin,
                averageLoss,
                averageWin == null || averageLoss == null || averageLoss.signum() == 0
                        ? null
                        : divide(averageWin, averageLoss.abs()),
                closed.isEmpty() ? null : divide(closedRealizedPnl, closedCount),
                normalized(totalRealizedPnl),
                average(closed, TradeEpisode::returnPct),
                holdingBarsSamples.isEmpty()
                        ? null
                        : divide(
                                holdingBarsSamples.stream()
                                        .map(TradeEpisode::holdingBars)
                                        .map(BigDecimal::valueOf)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                                BigDecimal.valueOf(holdingBarsSamples.size())
                        ),
                closed.isEmpty()
                        ? null
                        : divide(
                                closed.stream().map(TradeEpisode::entryCount).map(BigDecimal::valueOf)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                                closedCount
                        ),
                closed.isEmpty()
                        ? null
                        : divide(
                                closed.stream().map(TradeEpisode::exitCount).map(BigDecimal::valueOf)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                                closedCount
                        ),
                references(wins),
                references(losses),
                references(breakeven),
                references(open)
        );
    }

    private void validateEpisode(Long resultChartId, TradeEpisode episode) {
        if (episode == null || episode.chartId() == null || !resultChartId.equals(episode.chartId())) {
            throw new TradeEpisodeDataException("Episode chart id does not match its analysis result");
        }
        if (episode.episodeIndex() < 1 || episode.realizedPnl() == null || episode.returnPct() == null) {
            throw new TradeEpisodeDataException("Episode has incomplete deterministic metrics");
        }
    }

    private List<TradeEpisodeReference> references(List<TradeEpisode> episodes) {
        return episodes.stream().map(this::reference).toList();
    }

    private TradeEpisodeReference reference(TradeEpisode episode) {
        return new TradeEpisodeReference(episode.chartId(), episode.episodeIndex());
    }

    private BigDecimal average(
            List<TradeEpisode> episodes,
            java.util.function.Function<TradeEpisode, BigDecimal> mapper
    ) {
        return episodes.isEmpty()
                ? null
                : divide(sum(episodes, mapper), BigDecimal.valueOf(episodes.size()));
    }

    private BigDecimal sum(
            List<TradeEpisode> episodes,
            java.util.function.Function<TradeEpisode, BigDecimal> mapper
    ) {
        return episodes.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return normalized(numerator.divide(denominator, SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal normalized(BigDecimal value) {
        return value.stripTrailingZeros();
    }
}
