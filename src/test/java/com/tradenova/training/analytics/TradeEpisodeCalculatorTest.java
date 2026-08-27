package com.tradenova.training.analytics;

import com.tradenova.training.entity.TradeSide;
import com.tradenova.training.entity.TrainingTrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeEpisodeCalculatorTest {

    private final TradeEpisodeCalculator calculator = new TradeEpisodeCalculator();

    @Test
    void reconstructsSingleWinningEpisode() {
        TradeEpisode episode = only(
                trade(1, TradeSide.BUY, 10, 100, 1000, 11L),
                trade(2, TradeSide.SELL, 10, 110, 2000, 12L)
        );

        decimalEquals("100", episode.realizedPnl());
        decimalEquals("10", episode.returnPct());
        assertTrue(episode.closed());
        assertEquals(11L, episode.firstEntryRiskRuleHistoryId());
        assertEquals(12L, episode.lastExitRiskRuleHistoryId());
    }

    @Test
    void usesAverageCostAcrossMultipleEntries() {
        TradeEpisode episode = only(
                trade(1, TradeSide.BUY, 10, 100, 1000, null),
                trade(2, TradeSide.BUY, 10, 120, 2000, null),
                trade(3, TradeSide.SELL, 20, 130, 3000, null)
        );

        decimalEquals("110", episode.weightedEntryPrice());
        decimalEquals("130", episode.weightedExitPrice());
        decimalEquals("400", episode.realizedPnl());
    }

    @Test
    void accumulatesRealizedPnlAcrossPartialExits() {
        TradeEpisode episode = only(
                trade(1, TradeSide.BUY, 10, 100, 1000, null),
                trade(2, TradeSide.SELL, 5, 120, 2000, null),
                trade(3, TradeSide.SELL, 5, 80, 3000, null)
        );

        decimalEquals("0", episode.realizedPnl());
        decimalEquals("100", episode.weightedExitPrice());
        assertEquals(2, episode.exitCount());
    }

    @Test
    void startsAnotherEpisodeAfterPositionIsClosed() {
        List<TradeEpisode> episodes = calculator.calculate(List.of(
                trade(1, TradeSide.BUY, 10, 100, 1000, null),
                trade(2, TradeSide.SELL, 10, 110, 2000, null),
                trade(3, TradeSide.BUY, 5, 90, 3000, null),
                trade(4, TradeSide.SELL, 5, 100, 4000, null)
        ));

        assertEquals(2, episodes.size());
        assertEquals(List.of(1L, 2L), episodes.get(0).allTradeIds());
        assertEquals(List.of(3L, 4L), episodes.get(1).allTradeIds());
    }

    @Test
    void includesOpenEpisodeWithoutUnrealizedPnl() {
        TradeEpisode episode = only(
                trade(1, TradeSide.BUY, 10, 100, 1000, null),
                trade(2, TradeSide.BUY, 5, 120, 2000, null)
        );

        assertFalse(episode.closed());
        decimalEquals("15", episode.remainingQty());
        decimalEquals("0", episode.realizedPnl());
        assertNull(episode.closedAtCandleTime());
        assertNull(episode.weightedExitPrice());
        assertNull(episode.holdingBars());
    }

    @Test
    void canonicalIdOrderWinsWhenTradesShareCandle() {
        TradeEpisode episode = only(
                trade(3, TradeSide.SELL, 15, 130, 1000, 30L),
                trade(1, TradeSide.BUY, 10, 100, 1000, 10L),
                trade(2, TradeSide.BUY, 5, 120, 1000, 20L)
        );

        assertEquals(List.of(1L, 2L, 3L), episode.allTradeIds());
        assertEquals(10L, episode.firstEntryRiskRuleHistoryId());
        assertEquals(30L, episode.lastExitRiskRuleHistoryId());
    }

    @Test
    void resolvesHoldingBarsFromImmutableChartCandleIndexes() {
        List<TrainingTrade> trades = List.of(
                trade(1, TradeSide.BUY, 10, 100, 1000, null),
                trade(2, TradeSide.SELL, 10, 110, 3000, null)
        );

        TradeEpisode episode = calculator.calculate(trades, Map.of(1000L, 4, 3000L, 9)).get(0);

        assertEquals(5, episode.holdingBars());
    }

    @Test
    void rejectsOversellRatherThanSilentlyClampingIt() {
        List<TrainingTrade> trades = List.of(
                trade(1, TradeSide.BUY, 5, 100, 1000, null),
                trade(2, TradeSide.SELL, 10, 110, 2000, null)
        );

        TradeEpisodeDataException error = assertThrows(
                TradeEpisodeDataException.class,
                () -> calculator.calculate(trades)
        );
        assertTrue(error.getMessage().contains("id=2"));
        assertTrue(error.getMessage().contains("exceeds"));
    }

    private TradeEpisode only(TrainingTrade... trades) {
        List<TradeEpisode> result = calculator.calculate(List.of(trades));
        assertEquals(1, result.size());
        return result.get(0);
    }

    private TrainingTrade trade(
            long id,
            TradeSide side,
            int qty,
            int price,
            long candleTime,
            Long riskHistoryId
    ) {
        return TrainingTrade.builder()
                .id(id)
                .chartId(100L)
                .accountId(200L)
                .symbolId(300L)
                .side(side)
                .qty(BigDecimal.valueOf(qty))
                .price(BigDecimal.valueOf(price))
                .candleTime(candleTime)
                .riskRuleHistoryId(riskHistoryId)
                .build();
    }

    private void decimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
