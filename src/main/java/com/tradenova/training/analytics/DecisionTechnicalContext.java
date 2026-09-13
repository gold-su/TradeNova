package com.tradenova.training.analytics;

import java.util.List;

/** Observable market facts calculated at a strictly bounded candle index. */
public record DecisionTechnicalContext(
        Long chartId,
        Integer asOfProgressIndex,
        Long asOfCandleTime,
        Integer barsAvailable,
        Double currentClose,
        Double return5BarsPct,
        Double return20BarsPct,
        Double sma20,
        Double sma60,
        Double priceVsSma20Pct,
        Double priceVsSma60Pct,
        Double rsi14,
        Double atr14,
        Double atr14Pct,
        Double currentVolume,
        Double averageVolume20,
        Double volumeRatio20,
        Double high20,
        Double low20,
        Double distanceToHigh20Pct,
        Double distanceToLow20Pct,
        Double high60,
        Double low60,
        Double distanceToHigh60Pct,
        Double distanceToLow60Pct,
        List<SwingPoint> recentSwingHighs,
        List<SwingPoint> recentSwingLows
) {
    public record SwingPoint(Integer candleIndex, Long candleTime, Double price) {}
}
