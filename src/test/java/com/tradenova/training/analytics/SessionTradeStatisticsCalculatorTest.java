package com.tradenova.training.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionTradeStatisticsCalculatorTest {

    private final SessionTradeStatisticsCalculator calculator = new SessionTradeStatisticsCalculator();

    @Test
    void aggregatesMultipleChartsWithClosedAndOpenEpisodes() {
        TradeEpisode winOne = episode(10L, 1, true, "100", "10", 5, 1, 1);
        TradeEpisode loss = episode(10L, 2, true, "-50", "-5", null, 2, 1);
        TradeEpisode openWithPartialPnl = episode(10L, 3, false, "20", "2", null, 1, 1);
        TradeEpisode breakeven = episode(20L, 1, true, "0", "0", 2, 1, 2);
        TradeEpisode winTwo = episode(20L, 2, true, "200", "20", 10, 2, 1);

        SessionTradeStatistics result = calculator.calculate(List.of(
                new TradeEpisodeAnalysisResult(10L, List.of(winOne, loss, openWithPartialPnl)),
                new TradeEpisodeAnalysisResult(20L, List.of(breakeven, winTwo))
        ));

        assertEquals(5, result.totalEpisodeCount());
        assertEquals(4, result.closedEpisodeCount());
        assertEquals(1, result.openEpisodeCount());
        assertEquals(2, result.winningClosedEpisodeCount());
        assertEquals(1, result.losingClosedEpisodeCount());
        assertEquals(1, result.breakevenClosedEpisodeCount());
        decimalEquals("0.5", result.winRate());
        decimalEquals("150", result.averageWinningRealizedPnl());
        decimalEquals("-50", result.averageLosingRealizedPnl());
        decimalEquals("3", result.payoffRatio());
        decimalEquals("62.5", result.expectancy());
        decimalEquals("270", result.totalRealizedPnl());
        decimalEquals("6.25", result.averageReturnPct());
        assertEquals(3, result.holdingBarsSampleCount());
        decimalEquals("5.66666667", result.averageHoldingBars());
        decimalEquals("1.5", result.averageEntryCount());
        decimalEquals("1.25", result.averageExitCount());

        assertEquals(
                List.of(new TradeEpisodeReference(10L, 1), new TradeEpisodeReference(20L, 2)),
                result.winningEpisodes()
        );
        assertEquals(List.of(new TradeEpisodeReference(10L, 2)), result.losingEpisodes());
        assertEquals(List.of(new TradeEpisodeReference(20L, 1)), result.breakevenEpisodes());
        assertEquals(List.of(new TradeEpisodeReference(10L, 3)), result.openEpisodes());

        BigDecimal lossRate = BigDecimal.valueOf(result.losingClosedEpisodeCount())
                .divide(BigDecimal.valueOf(result.closedEpisodeCount()));
        BigDecimal probabilityExpectancy = result.winRate()
                .multiply(result.averageWinningRealizedPnl())
                .add(lossRate.multiply(result.averageLosingRealizedPnl()));
        decimalEquals(result.expectancy().toPlainString(), probabilityExpectancy);
    }

    @Test
    void usesNullForUndefinedClosedPerformanceMetrics() {
        SessionTradeStatistics result = calculator.calculate(List.of(
                new TradeEpisodeAnalysisResult(
                        10L,
                        List.of(episode(10L, 1, false, "20", "2", null, 1, 1))
                )
        ));

        assertEquals(1, result.totalEpisodeCount());
        assertEquals(0, result.closedEpisodeCount());
        decimalEquals("20", result.totalRealizedPnl());
        assertNull(result.winRate());
        assertNull(result.averageWinningRealizedPnl());
        assertNull(result.averageLosingRealizedPnl());
        assertNull(result.payoffRatio());
        assertNull(result.expectancy());
        assertNull(result.averageReturnPct());
        assertNull(result.averageHoldingBars());
        assertNull(result.averageEntryCount());
        assertNull(result.averageExitCount());
    }

    @Test
    void payoffIsNullWhenThereIsNoWinningOrLosingSample() {
        SessionTradeStatistics lossesOnly = calculator.calculate(List.of(
                new TradeEpisodeAnalysisResult(
                        10L,
                        List.of(episode(10L, 1, true, "-10", "-1", 1, 1, 1))
                )
        ));
        SessionTradeStatistics winsOnly = calculator.calculate(List.of(
                new TradeEpisodeAnalysisResult(
                        20L,
                        List.of(episode(20L, 1, true, "10", "1", 1, 1, 1))
                )
        ));

        assertNull(lossesOnly.averageWinningRealizedPnl());
        assertNull(lossesOnly.payoffRatio());
        assertNull(winsOnly.averageLosingRealizedPnl());
        assertNull(winsOnly.payoffRatio());
    }

    @Test
    void chartIdKeepsEqualEpisodeIndexesDistinct() {
        SessionTradeStatistics result = calculator.calculate(List.of(
                new TradeEpisodeAnalysisResult(
                        10L,
                        List.of(episode(10L, 1, true, "10", "1", 1, 1, 1))
                ),
                new TradeEpisodeAnalysisResult(
                        20L,
                        List.of(episode(20L, 1, true, "20", "2", 1, 1, 1))
                )
        ));

        assertEquals(
                List.of(new TradeEpisodeReference(10L, 1), new TradeEpisodeReference(20L, 1)),
                result.winningEpisodes()
        );
    }

    @Test
    void rejectsMismatchedChartEvidenceInsteadOfExcludingIt() {
        TradeEpisode episode = episode(20L, 1, true, "10", "1", 1, 1, 1);

        assertThrows(
                TradeEpisodeDataException.class,
                () -> calculator.calculate(List.of(new TradeEpisodeAnalysisResult(10L, List.of(episode))))
        );
    }

    private TradeEpisode episode(
            Long chartId,
            int episodeIndex,
            boolean closed,
            String realizedPnl,
            String returnPct,
            Integer holdingBars,
            int entryCount,
            int exitCount
    ) {
        return new TradeEpisode(
                episodeIndex,
                chartId,
                100L,
                chartId + 1_000,
                List.of((long) episodeIndex),
                exitCount == 0 ? List.of() : List.of((long) episodeIndex + 100),
                List.of((long) episodeIndex),
                1_000L,
                closed ? 2_000L : null,
                entryCount,
                exitCount,
                BigDecimal.TEN,
                closed ? BigDecimal.TEN : BigDecimal.ONE,
                BigDecimal.valueOf(100),
                exitCount == 0 ? null : BigDecimal.valueOf(110),
                new BigDecimal(realizedPnl),
                new BigDecimal(returnPct),
                holdingBars,
                closed,
                closed ? BigDecimal.ZERO : BigDecimal.valueOf(9),
                null,
                null
        );
    }

    private void decimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
