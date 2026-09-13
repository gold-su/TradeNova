package com.tradenova.training.analytics;

/** Compact candle representation for a bounded AI evidence window. */
public record VisibleOhlcvCandle(
        Integer idx, Long time, Double open, Double high, Double low, Double close, Double volume
) {}
