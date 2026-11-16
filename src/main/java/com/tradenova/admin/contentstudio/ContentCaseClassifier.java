package com.tradenova.admin.contentstudio;

import com.tradenova.market.entity.MarketCandle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Component
class ContentCaseClassifier {

    private static final int LOOKBACK_BARS = 20;
    private static final double EVENT_MOVE = 0.10;
    private static final double TREND_MOVE = 0.05;
    private static final double QUIET_RANGE = 0.08;
    private static final double BREAKOUT_BUFFER = 0.01;
    private static final double MAX_DAILY_DISCONTINUITY = 0.35;

    Optional<Evaluation> evaluate(List<MarketCandle> window, int analysisBars) {
        if (window == null || analysisBars < LOOKBACK_BARS || window.size() <= analysisBars) {
            return Optional.empty();
        }
        if (!hasValidPrices(window) || hasExcessiveDiscontinuity(window)) {
            return Optional.empty();
        }

        int decisionIndex = analysisBars - 1;
        int futureBars = window.size() - analysisBars;
        double decisionClose = window.get(decisionIndex).getClosePrice();
        double pre20Return = ratio(window.get(decisionIndex - LOOKBACK_BARS).getClosePrice(), decisionClose);
        double close5 = futureBars >= 5 ? ratio(decisionClose, window.get(decisionIndex + 5).getClosePrice()) : Double.NaN;
        double close10 = futureBars >= 10 ? ratio(decisionClose, window.get(decisionIndex + 10).getClosePrice()) : Double.NaN;
        double close20 = futureBars >= 20 ? ratio(decisionClose, window.get(decisionIndex + 20).getClosePrice()) : Double.NaN;
        double finalClose = ratio(decisionClose, window.get(window.size() - 1).getClosePrice());

        double futureMaxHigh = Double.NEGATIVE_INFINITY;
        double futureMinLow = Double.POSITIVE_INFINITY;
        for (int index = decisionIndex + 1; index < window.size(); index++) {
            futureMaxHigh = Math.max(futureMaxHigh, window.get(index).getHighPrice());
            futureMinLow = Math.min(futureMinLow, window.get(index).getLowPrice());
        }
        double maxHighReturn = ratio(decisionClose, futureMaxHigh);
        double minLowReturn = ratio(decisionClose, futureMinLow);

        int horizon20End = Math.min(window.size() - 1, decisionIndex + 20);
        double priorHigh = Double.NEGATIVE_INFINITY;
        double priorLow = Double.POSITIVE_INFINITY;
        for (int index = decisionIndex - LOOKBACK_BARS; index < decisionIndex; index++) {
            priorHigh = Math.max(priorHigh, window.get(index).getHighPrice());
            priorLow = Math.min(priorLow, window.get(index).getLowPrice());
        }
        double future20High = Double.NEGATIVE_INFINITY;
        double future20Low = Double.POSITIVE_INFINITY;
        double future10MinClose = Double.POSITIVE_INFINITY;
        double future10MaxClose = Double.NEGATIVE_INFINITY;
        for (int index = decisionIndex + 1; index <= horizon20End; index++) {
            MarketCandle candle = window.get(index);
            future20High = Math.max(future20High, candle.getHighPrice());
            future20Low = Math.min(future20Low, candle.getLowPrice());
            if (index <= decisionIndex + Math.min(10, futureBars)) {
                future10MinClose = Math.min(future10MinClose, candle.getClosePrice());
                future10MaxClose = Math.max(future10MaxClose, candle.getClosePrice());
            }
        }

        EnumSet<ContentCaseType> matched = EnumSet.noneOf(ContentCaseType.class);
        boolean upwardFalseBreakout = decisionClose > priorHigh * (1.0 + BREAKOUT_BUFFER)
                && future10MinClose < priorHigh;
        boolean downwardFalseBreakout = decisionClose < priorLow * (1.0 - BREAKOUT_BUFFER)
                && future10MaxClose > priorLow;
        if (upwardFalseBreakout || downwardFalseBreakout) {
            matched.add(ContentCaseType.FALSE_BREAKOUT);
        }
        if (isKnown(close20) && ((pre20Return >= EVENT_MOVE && close20 <= -EVENT_MOVE)
                || (pre20Return <= -EVENT_MOVE && close20 >= EVENT_MOVE))) {
            matched.add(ContentCaseType.TREND_REVERSAL);
        }
        double priorRange = (priorHigh - priorLow) / decisionClose;
        double future20Range = (future20High - future20Low) / decisionClose;
        if (priorRange <= QUIET_RANGE && future20Range >= EVENT_MOVE) {
            matched.add(ContentCaseType.VOLATILITY_EXPANSION);
        }
        if (isKnown(close20) && ((pre20Return >= TREND_MOVE && close20 >= EVENT_MOVE)
                || (pre20Return <= -TREND_MOVE && close20 <= -EVENT_MOVE))) {
            matched.add(ContentCaseType.TREND_CONTINUATION);
        }
        if (maxHighReturn >= EVENT_MOVE) {
            matched.add(ContentCaseType.SURGE);
        }
        if (minLowReturn <= -EVENT_MOVE) {
            matched.add(ContentCaseType.PLUNGE);
        }

        ContentCaseType primary = choosePrimary(matched, maxHighReturn, minLowReturn);
        return Optional.of(new Evaluation(
                primary,
                List.copyOf(matched),
                round(decisionClose),
                nullablePercent(close5),
                nullablePercent(close10),
                nullablePercent(close20),
                roundPercent(maxHighReturn),
                roundPercent(minLowReturn),
                roundPercent(finalClose)
        ));
    }

    private ContentCaseType choosePrimary(EnumSet<ContentCaseType> matched,
                                          double maxHighReturn,
                                          double minLowReturn) {
        List<ContentCaseType> priority = List.of(
                ContentCaseType.FALSE_BREAKOUT,
                ContentCaseType.TREND_REVERSAL,
                ContentCaseType.VOLATILITY_EXPANSION,
                ContentCaseType.TREND_CONTINUATION
        );
        for (ContentCaseType type : priority) {
            if (matched.contains(type)) {
                return type;
            }
        }
        if (matched.contains(ContentCaseType.SURGE) && matched.contains(ContentCaseType.PLUNGE)) {
            return maxHighReturn >= Math.abs(minLowReturn) ? ContentCaseType.SURGE : ContentCaseType.PLUNGE;
        }
        if (matched.contains(ContentCaseType.SURGE)) {
            return ContentCaseType.SURGE;
        }
        if (matched.contains(ContentCaseType.PLUNGE)) {
            return ContentCaseType.PLUNGE;
        }
        return null;
    }

    private boolean hasValidPrices(List<MarketCandle> candles) {
        for (MarketCandle candle : candles) {
            double open = candle.getOpenPrice();
            double high = candle.getHighPrice();
            double low = candle.getLowPrice();
            double close = candle.getClosePrice();
            if (!isPositiveFinite(open) || !isPositiveFinite(high)
                    || !isPositiveFinite(low) || !isPositiveFinite(close)
                    || candle.getVolume() == null || candle.getVolume() < 0
                    || low > Math.min(open, close) || high < Math.max(open, close) || low > high) {
                return false;
            }
        }
        return true;
    }

    private boolean hasExcessiveDiscontinuity(List<MarketCandle> candles) {
        for (int index = 1; index < candles.size(); index++) {
            double previousClose = candles.get(index - 1).getClosePrice();
            MarketCandle current = candles.get(index);
            if (Math.abs(ratio(previousClose, current.getOpenPrice())) > MAX_DAILY_DISCONTINUITY
                    || Math.abs(ratio(previousClose, current.getClosePrice())) > MAX_DAILY_DISCONTINUITY) {
                return true;
            }
        }
        return false;
    }

    private double ratio(double base, double value) {
        return value / base - 1.0;
    }

    private boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private boolean isKnown(double value) {
        return !Double.isNaN(value);
    }

    private Double nullablePercent(double value) {
        return isKnown(value) ? roundPercent(value) : null;
    }

    private double roundPercent(double ratio) {
        return round(ratio * 100.0);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    record Evaluation(
            ContentCaseType primaryType,
            List<ContentCaseType> matchedTypes,
            double decisionClose,
            Double closeReturn5dPct,
            Double closeReturn10dPct,
            Double closeReturn20dPct,
            double futureMaxHighReturnPct,
            double futureMinLowReturnPct,
            double futureFinalCloseReturnPct
    ) {
        boolean isMeaningful() {
            return primaryType != null;
        }

        ContentCaseAnalysisResponse toResponse() {
            return new ContentCaseAnalysisResponse(primaryType, new ArrayList<>(matchedTypes), decisionClose,
                    closeReturn5dPct, closeReturn10dPct, closeReturn20dPct,
                    futureMaxHighReturnPct, futureMinLowReturnPct, futureFinalCloseReturnPct);
        }
    }
}
