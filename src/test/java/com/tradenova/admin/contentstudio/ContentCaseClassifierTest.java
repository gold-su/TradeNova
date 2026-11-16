package com.tradenova.admin.contentstudio;

import com.tradenova.market.entity.MarketCandle;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentCaseClassifierTest {

    private static final int ANALYSIS_BARS = 80;
    private static final int TOTAL_BARS = 120;
    private final ContentCaseClassifier classifier = new ContentCaseClassifier();

    @Test
    void classifiesSixMeaningfulMovementTypes() {
        assertThat(classifier.evaluate(surgeWindow(), ANALYSIS_BARS).orElseThrow().primaryType())
                .isEqualTo(ContentCaseType.SURGE);
        assertThat(classifier.evaluate(plungeWindow(), ANALYSIS_BARS).orElseThrow().primaryType())
                .isEqualTo(ContentCaseType.PLUNGE);
        assertThat(classifier.evaluate(continuationWindow(), ANALYSIS_BARS).orElseThrow().primaryType())
                .isEqualTo(ContentCaseType.TREND_CONTINUATION);
        assertThat(classifier.evaluate(reversalWindow(), ANALYSIS_BARS).orElseThrow().primaryType())
                .isEqualTo(ContentCaseType.TREND_REVERSAL);
        assertThat(classifier.evaluate(falseBreakoutWindow(), ANALYSIS_BARS).orElseThrow().primaryType())
                .isEqualTo(ContentCaseType.FALSE_BREAKOUT);
        assertThat(classifier.evaluate(volatilityExpansionWindow(), ANALYSIS_BARS).orElseThrow().primaryType())
                .isEqualTo(ContentCaseType.VOLATILITY_EXPANSION);
    }

    @Test
    void keepsMaximumMinimumAndFinalReturnsSeparate() {
        List<MarketCandle> candles = surgeWindow();
        ContentCaseClassifier.Evaluation evaluation = classifier.evaluate(candles, ANALYSIS_BARS).orElseThrow();

        assertThat(evaluation.futureMaxHighReturnPct()).isGreaterThanOrEqualTo(10.0);
        assertThat(evaluation.futureMinLowReturnPct()).isLessThan(0.0);
        assertThat(evaluation.futureFinalCloseReturnPct()).isEqualTo(0.0);
        assertThat(evaluation.closeReturn5dPct()).isEqualTo(0.0);
        assertThat(evaluation.closeReturn10dPct()).isEqualTo(0.0);
        assertThat(evaluation.closeReturn20dPct()).isEqualTo(0.0);
    }

    @Test
    void rejectsInvalidOhlcAndExcessivePriceDiscontinuity() {
        List<MarketCandle> invalidOhlc = new ArrayList<>(flatWindow());
        invalidOhlc.set(50, candle(50, 100, 99, 98, 100));
        assertThat(classifier.evaluate(invalidOhlc, ANALYSIS_BARS)).isEmpty();

        List<MarketCandle> discontinuous = new ArrayList<>(flatWindow());
        discontinuous.set(50, candle(50, 150, 151, 149, 150));
        assertThat(classifier.evaluate(discontinuous, ANALYSIS_BARS)).isEmpty();
    }

    private List<MarketCandle> surgeWindow() {
        List<MarketCandle> candles = oscillatingPriorWindow();
        candles.set(90, candle(90, 100, 112, 99.5, 100));
        return candles;
    }

    private List<MarketCandle> plungeWindow() {
        List<MarketCandle> candles = oscillatingPriorWindow();
        candles.set(90, candle(90, 100, 100.5, 88, 100));
        return candles;
    }

    private List<MarketCandle> continuationWindow() {
        double[] closes = flatCloses();
        closes[59] = 90;
        for (int index = 60; index <= 79; index++) {
            closes[index] = 90 + (index - 59) * 0.5;
        }
        for (int index = 80; index <= 99; index++) {
            closes[index] = 100 + (index - 79) * 0.6;
        }
        return candles(closes);
    }

    private List<MarketCandle> reversalWindow() {
        double[] closes = flatCloses();
        closes[59] = 90;
        for (int index = 60; index <= 79; index++) {
            closes[index] = 90 + (index - 59) * 0.75;
        }
        for (int index = 80; index <= 99; index++) {
            closes[index] = 105 - (index - 79) * 0.65;
        }
        return candles(closes);
    }

    private List<MarketCandle> falseBreakoutWindow() {
        List<MarketCandle> candles = flatWindow();
        candles.set(79, candle(79, 100, 102.5, 99.5, 102));
        for (int index = 80; index <= 89; index++) {
            candles.set(index, candle(index, 99, 99.5, 97.5, 98));
        }
        return candles;
    }

    private List<MarketCandle> volatilityExpansionWindow() {
        List<MarketCandle> candles = flatWindow();
        candles.set(90, candle(90, 100, 106, 94, 100));
        return candles;
    }

    private List<MarketCandle> oscillatingPriorWindow() {
        double[] closes = flatCloses();
        for (int index = 59; index <= 78; index++) {
            closes[index] = index % 2 == 0 ? 95 : 105;
        }
        closes[79] = 100;
        return candles(closes);
    }

    private List<MarketCandle> flatWindow() {
        return candles(flatCloses());
    }

    private double[] flatCloses() {
        double[] closes = new double[TOTAL_BARS];
        java.util.Arrays.fill(closes, 100.0);
        return closes;
    }

    private List<MarketCandle> candles(double[] closes) {
        List<MarketCandle> candles = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) {
            candles.add(candle(index, closes[index], closes[index] + 0.5, closes[index] - 0.5, closes[index]));
        }
        return candles;
    }

    private MarketCandle candle(int index, double open, double high, double low, double close) {
        return MarketCandle.builder()
                .candleDate(LocalDate.of(2020, 1, 1).plusDays(index))
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(100_000L)
                .build();
    }
}
