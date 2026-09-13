package com.tradenova.training.analytics;

import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Calculates canonical indicators without ever reading beyond the requested as-of index. */
@Service
@RequiredArgsConstructor
public class DecisionTechnicalContextService {
    private static final int PIVOT_RADIUS = 2;
    private static final int MAX_SWINGS = 3;
    private static final int OHLCV_LOOKBACK = 60;

    private final TrainingSessionCandleRepository candleRepository;

    public DecisionTechnicalContext calculate(Long chartId, Integer asOfProgressIndex) {
        List<TrainingSessionCandle> candles = visibleCandles(chartId, asOfProgressIndex);
        if (candles.isEmpty()) {
            return null;
        }
        TrainingSessionCandle current = candles.get(candles.size() - 1);
        Double sma20 = averageClose(candles, 20);
        Double sma60 = averageClose(candles, 60);
        Double atr14 = atr(candles, 14);
        Range range20 = range(candles, 20);
        Range range60 = range(candles, 60);
        return new DecisionTechnicalContext(
                chartId, current.getIdx(), current.getT(), candles.size(), current.getC(),
                returnPct(candles, 5), returnPct(candles, 20), sma20, sma60,
                percentFrom(current.getC(), sma20), percentFrom(current.getC(), sma60),
                rsi(candles, 14), atr14, ratioPct(atr14, current.getC()), current.getV(),
                averageVolume(candles, 20), ratio(current.getV(), averageVolume(candles, 20)),
                high(range20), low(range20), distanceToHigh(current.getC(), high(range20)),
                distanceToLow(current.getC(), low(range20)), high(range60), low(range60),
                distanceToHigh(current.getC(), high(range60)), distanceToLow(current.getC(), low(range60)),
                swings(candles, true), swings(candles, false));
    }

    public List<VisibleOhlcvCandle> boundedOhlcv(Long chartId, Integer asOfProgressIndex) {
        List<TrainingSessionCandle> visible = visibleCandles(chartId, asOfProgressIndex);
        int from = Math.max(0, visible.size() - OHLCV_LOOKBACK);
        return visible.subList(from, visible.size()).stream()
                .map(c -> new VisibleOhlcvCandle(c.getIdx(), c.getT(), c.getO(), c.getH(), c.getL(), c.getC(), c.getV()))
                .toList();
    }

    private List<TrainingSessionCandle> visibleCandles(Long chartId, Integer asOfProgressIndex) {
        if (asOfProgressIndex == null) return List.of();
        return candleRepository.findAllByChartIdAndIdxLessThanEqualOrderByIdxAsc(chartId, asOfProgressIndex)
                .stream().filter(c -> c.getIdx() <= asOfProgressIndex)
                .sorted(Comparator.comparing(TrainingSessionCandle::getIdx)).toList();
    }

    private Double averageClose(List<TrainingSessionCandle> bars, int period) {
        if (bars.size() < period) return null;
        return bars.subList(bars.size() - period, bars.size()).stream().mapToDouble(TrainingSessionCandle::getC).average().orElseThrow();
    }

    private Double averageVolume(List<TrainingSessionCandle> bars, int period) {
        if (bars.size() < period) return null;
        return bars.subList(bars.size() - period, bars.size()).stream().mapToDouble(TrainingSessionCandle::getV).average().orElseThrow();
    }

    private Double returnPct(List<TrainingSessionCandle> bars, int period) {
        if (bars.size() <= period) return null;
        return ratioPct(bars.get(bars.size() - 1).getC() - bars.get(bars.size() - 1 - period).getC(),
                bars.get(bars.size() - 1 - period).getC());
    }

    private Double rsi(List<TrainingSessionCandle> bars, int period) {
        if (bars.size() <= period) return null;
        double gains = 0, losses = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) {
            double change = bars.get(i).getC() - bars.get(i - 1).getC();
            if (change > 0) gains += change; else losses -= change;
        }
        if (gains == 0 && losses == 0) return null;
        if (losses == 0) return 100.0;
        return 100.0 - 100.0 / (1.0 + gains / losses);
    }

    private Double atr(List<TrainingSessionCandle> bars, int period) {
        if (bars.size() <= period) return null;
        double sum = 0;
        for (int i = bars.size() - period; i < bars.size(); i++) {
            TrainingSessionCandle bar = bars.get(i);
            double previousClose = bars.get(i - 1).getC();
            sum += Math.max(bar.getH() - bar.getL(), Math.max(Math.abs(bar.getH() - previousClose), Math.abs(bar.getL() - previousClose)));
        }
        return sum / period;
    }

    private Range range(List<TrainingSessionCandle> bars, int period) {
        if (bars.size() < period) return null;
        List<TrainingSessionCandle> window = bars.subList(bars.size() - period, bars.size());
        return new Range(window.stream().mapToDouble(TrainingSessionCandle::getH).max().orElseThrow(),
                window.stream().mapToDouble(TrainingSessionCandle::getL).min().orElseThrow());
    }

    private List<DecisionTechnicalContext.SwingPoint> swings(List<TrainingSessionCandle> bars, boolean high) {
        List<DecisionTechnicalContext.SwingPoint> points = new ArrayList<>();
        for (int i = PIVOT_RADIUS; i < bars.size() - PIVOT_RADIUS; i++) {
            double value = high ? bars.get(i).getH() : bars.get(i).getL();
            boolean pivot = true;
            for (int offset = 1; offset <= PIVOT_RADIUS; offset++) {
                double left = high ? bars.get(i - offset).getH() : bars.get(i - offset).getL();
                double right = high ? bars.get(i + offset).getH() : bars.get(i + offset).getL();
                pivot &= high ? value > left && value > right : value < left && value < right;
            }
            if (pivot) points.add(new DecisionTechnicalContext.SwingPoint(bars.get(i).getIdx(), bars.get(i).getT(), value));
        }
        int from = Math.max(0, points.size() - MAX_SWINGS);
        List<DecisionTechnicalContext.SwingPoint> latest = new ArrayList<>(points.subList(from, points.size()));
        latest.sort(Comparator.comparing(DecisionTechnicalContext.SwingPoint::candleIndex).reversed());
        return List.copyOf(latest);
    }

    private Double percentFrom(Double value, Double reference) { return value == null || reference == null ? null : ratioPct(value - reference, reference); }
    private Double ratio(Double value, Double denominator) { return value == null || denominator == null || denominator == 0 ? null : value / denominator; }
    private Double ratioPct(Double value, Double denominator) { Double result = ratio(value, denominator); return result == null ? null : result * 100.0; }
    private Double distanceToHigh(Double close, Double high) { return close == null || high == null ? null : ratioPct(high - close, close); }
    private Double distanceToLow(Double close, Double low) { return close == null || low == null ? null : ratioPct(close - low, close); }
    private Double high(Range range) { return range == null ? null : range.high; }
    private Double low(Range range) { return range == null ? null : range.low; }
    private record Range(double high, double low) {}
}
