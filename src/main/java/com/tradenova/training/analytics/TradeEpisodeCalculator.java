package com.tradenova.training.analytics;

import com.tradenova.training.entity.TradeSide;
import com.tradenova.training.entity.TrainingTrade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs long-only position episodes using trade id as the canonical order.
 */
@Component
public class TradeEpisodeCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int DIVISION_SCALE = 10;
    private static final int POSITION_AVERAGE_SCALE = 4;

    public List<TradeEpisode> calculate(List<TrainingTrade> trades) {
        return calculate(trades, Map.of());
    }

    /**
     * @param candleIndexes candle time to the chart's immutable candle index
     */
    public List<TradeEpisode> calculate(
            List<TrainingTrade> trades,
            Map<Long, Integer> candleIndexes
    ) {
        List<TrainingTrade> ordered = new ArrayList<>(trades);
        validateAndSort(ordered);

        List<TradeEpisode> episodes = new ArrayList<>();
        EpisodeAccumulator current = null;

        for (TrainingTrade trade : ordered) {
            validateTrade(trade);
            if (trade.getSide() == TradeSide.BUY) {
                if (current == null) {
                    current = new EpisodeAccumulator(episodes.size() + 1, trade);
                }
                current.buy(trade);
                continue;
            }

            if (current == null || current.remainingQty.signum() == 0) {
                throw invalid(trade, "SELL has no reconstructed position");
            }
            if (trade.getQty().compareTo(current.remainingQty) > 0) {
                throw invalid(trade, "SELL quantity exceeds reconstructed position: held="
                        + current.remainingQty + ", sold=" + trade.getQty());
            }

            current.sell(trade);
            if (current.remainingQty.signum() == 0) {
                episodes.add(current.toEpisode(candleIndexes));
                current = null;
            }
        }

        if (current != null) {
            episodes.add(current.toEpisode(candleIndexes));
        }
        return List.copyOf(episodes);
    }

    private void validateAndSort(List<TrainingTrade> trades) {
        if (trades.stream().anyMatch(trade -> trade == null || trade.getId() == null)) {
            throw new TradeEpisodeDataException("Every trade requires a canonical id");
        }
        trades.sort(Comparator.comparing(TrainingTrade::getId));
        for (int i = 1; i < trades.size(); i++) {
            if (trades.get(i - 1).getId().equals(trades.get(i).getId())) {
                throw invalid(trades.get(i), "duplicate canonical trade id");
            }
        }
    }

    private void validateTrade(TrainingTrade trade) {
        if (trade.getSide() == null) {
            throw invalid(trade, "side is missing");
        }
        if (trade.getQty() == null || trade.getQty().signum() <= 0) {
            throw invalid(trade, "quantity must be positive");
        }
        if (trade.getPrice() == null || trade.getPrice().signum() <= 0) {
            throw invalid(trade, "price must be positive");
        }
        if (trade.getChartId() == null || trade.getAccountId() == null || trade.getSymbolId() == null) {
            throw invalid(trade, "chart, account, and symbol are required");
        }
    }

    private TradeEpisodeDataException invalid(TrainingTrade trade, String reason) {
        return new TradeEpisodeDataException("Invalid trade id=" + trade.getId() + ": " + reason);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, DIVISION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private static BigDecimal positionAverage(BigDecimal cost, BigDecimal quantity) {
        return cost.divide(quantity, POSITION_AVERAGE_SCALE, RoundingMode.HALF_UP);
    }

    private static final class EpisodeAccumulator {
        private final int index;
        private final Long chartId;
        private final Long accountId;
        private final Long symbolId;
        private final List<Long> entryIds = new ArrayList<>();
        private final List<Long> exitIds = new ArrayList<>();
        private final List<Long> allIds = new ArrayList<>();
        private final Long openedAt;
        private final Long firstEntryRiskHistoryId;
        private BigDecimal entryQty = BigDecimal.ZERO;
        private BigDecimal exitQty = BigDecimal.ZERO;
        private BigDecimal entryCost = BigDecimal.ZERO;
        private BigDecimal exitProceeds = BigDecimal.ZERO;
        private BigDecimal remainingQty = BigDecimal.ZERO;
        private BigDecimal remainingCost = BigDecimal.ZERO;
        private BigDecimal averageCost = BigDecimal.ZERO;
        private BigDecimal realizedPnl = BigDecimal.ZERO;
        private Long closedAt;
        private Long lastExitRiskHistoryId;

        private EpisodeAccumulator(int index, TrainingTrade firstEntry) {
            this.index = index;
            this.chartId = firstEntry.getChartId();
            this.accountId = firstEntry.getAccountId();
            this.symbolId = firstEntry.getSymbolId();
            this.openedAt = firstEntry.getCandleTime();
            this.firstEntryRiskHistoryId = firstEntry.getRiskRuleHistoryId();
        }

        private void verifyIdentity(TrainingTrade trade) {
            if (!chartId.equals(trade.getChartId())
                    || !accountId.equals(trade.getAccountId())
                    || !symbolId.equals(trade.getSymbolId())) {
                throw new TradeEpisodeDataException("Trade id=" + trade.getId()
                        + " changes chart/account/symbol inside an episode");
            }
        }

        private void buy(TrainingTrade trade) {
            verifyIdentity(trade);
            entryIds.add(trade.getId());
            allIds.add(trade.getId());
            entryQty = entryQty.add(trade.getQty());
            entryCost = entryCost.add(trade.getPrice().multiply(trade.getQty()));
            remainingQty = remainingQty.add(trade.getQty());
            remainingCost = remainingCost.add(trade.getPrice().multiply(trade.getQty()));
            // Match TrainingTradeService/PaperPosition's average-cost scale and rounding policy.
            averageCost = positionAverage(remainingCost, remainingQty);
        }

        private void sell(TrainingTrade trade) {
            verifyIdentity(trade);
            exitIds.add(trade.getId());
            allIds.add(trade.getId());
            exitQty = exitQty.add(trade.getQty());
            exitProceeds = exitProceeds.add(trade.getPrice().multiply(trade.getQty()));
            realizedPnl = realizedPnl.add(
                    trade.getPrice().subtract(averageCost).multiply(trade.getQty())
            );
            remainingCost = remainingCost.subtract(averageCost.multiply(trade.getQty()));
            remainingQty = remainingQty.subtract(trade.getQty());
            closedAt = trade.getCandleTime();
            lastExitRiskHistoryId = trade.getRiskRuleHistoryId();
        }

        private TradeEpisode toEpisode(Map<Long, Integer> candleIndexes) {
            boolean closed = remainingQty.signum() == 0;
            Integer holdingBars = null;
            if (closed && openedAt != null && closedAt != null) {
                Integer openIndex = candleIndexes.get(openedAt);
                Integer closeIndex = candleIndexes.get(closedAt);
                if (openIndex != null && closeIndex != null) {
                    holdingBars = closeIndex - openIndex;
                }
            }
            BigDecimal returnPct = entryCost.signum() == 0
                    ? BigDecimal.ZERO
                    : divide(realizedPnl.multiply(ONE_HUNDRED), entryCost);

            return new TradeEpisode(
                    index, chartId, accountId, symbolId,
                    entryIds, exitIds, allIds,
                    openedAt, closed ? closedAt : null,
                    entryIds.size(), exitIds.size(), entryQty, exitQty,
                    divide(entryCost, entryQty),
                    exitQty.signum() == 0 ? null : divide(exitProceeds, exitQty),
                    realizedPnl.stripTrailingZeros(), returnPct, holdingBars,
                    closed, remainingQty, firstEntryRiskHistoryId, lastExitRiskHistoryId
            );
        }
    }
}
