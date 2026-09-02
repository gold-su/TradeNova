package com.tradenova.training.dto;

import com.tradenova.training.entity.TrainingSessionCandle;

/** A candle made visible by the current advance request. */
public record RevealedCandleResponse(
        Integer idx,
        long t,
        double o,
        double h,
        double l,
        double c,
        double v
) {
    public static RevealedCandleResponse from(TrainingSessionCandle candle) {
        return new RevealedCandleResponse(
                candle.getIdx(),
                candle.getT(),
                candle.getO(),
                candle.getH(),
                candle.getL(),
                candle.getC(),
                candle.getV()
        );
    }
}
