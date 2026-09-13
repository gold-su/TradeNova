package com.tradenova.training.analytics;

import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionTechnicalContextServiceTest {
    private static final long CHART_ID = 7L;
    private final TrainingSessionCandleRepository repository = mock(TrainingSessionCandleRepository.class);
    private final DecisionTechnicalContextService service = new DecisionTechnicalContextService(repository);

    @Test
    void calculatesCanonicalIndicatorsAndIgnoresRepositoryFutureBars() {
        List<TrainingSessionCandle> all = trendBars(65);
        when(repository.findAllByChartIdAndIdxLessThanEqualOrderByIdxAsc(CHART_ID, 59)).thenReturn(all);

        DecisionTechnicalContext result = service.calculate(CHART_ID, 59);

        assertEquals(60, result.barsAvailable());
        assertEquals(60.0, result.currentClose());
        assertEquals(50.5, result.sma20(), 1e-9);
        assertEquals(30.5, result.sma60(), 1e-9);
        assertEquals(100.0, result.rsi14(), 1e-9);
        assertEquals(1.5, result.atr14(), 1e-9);
        assertEquals(2.5, result.atr14Pct(), 1e-9);
        assertEquals(149.5, result.averageVolume20(), 1e-9);
        assertEquals(159.0 / 149.5, result.volumeRatio20(), 1e-9);
        assertEquals(60.5, result.high20(), 1e-9);
        assertEquals(40.5, result.low20(), 1e-9);
        assertEquals(60.5, result.high60(), 1e-9);
        assertEquals(0.5, result.low60(), 1e-9);
        assertEquals(60, service.boundedOhlcv(CHART_ID, 59).size());
        assertTrue(service.boundedOhlcv(CHART_ID, 59).stream().allMatch(c -> c.idx() <= 59));
    }

    @Test
    void unavailableIndicatorsAreNullAndZeroDenominatorsStayFinite() {
        List<TrainingSessionCandle> bars = List.of(bar(0, 0, 1, -1, 0, 0));
        when(repository.findAllByChartIdAndIdxLessThanEqualOrderByIdxAsc(CHART_ID, 0)).thenReturn(bars);

        DecisionTechnicalContext result = service.calculate(CHART_ID, 0);

        assertNull(result.sma20());
        assertNull(result.sma60());
        assertNull(result.rsi14());
        assertNull(result.atr14());
        assertNull(result.volumeRatio20());
        assertNull(result.high20());
        assertTrue(result.recentSwingHighs().isEmpty());
    }

    @Test
    void includesOnlyFullyConfirmedStrictPivots() {
        double[] highs = {1, 2, 5, 2, 1, 2, 6, 2};
        List<TrainingSessionCandle> bars = IntStream.range(0, highs.length)
                .mapToObj(i -> bar(i, 1, highs[i], -highs[i], 1, 10)).toList();
        when(repository.findAllByChartIdAndIdxLessThanEqualOrderByIdxAsc(CHART_ID, 7)).thenReturn(bars);

        DecisionTechnicalContext result = service.calculate(CHART_ID, 7);

        assertEquals(List.of(2), result.recentSwingHighs().stream().map(DecisionTechnicalContext.SwingPoint::candleIndex).toList());
        assertEquals(List.of(2), result.recentSwingLows().stream().map(DecisionTechnicalContext.SwingPoint::candleIndex).toList());
        assertFalse(result.recentSwingHighs().stream().anyMatch(p -> p.candleIndex() == 6));
    }

    private List<TrainingSessionCandle> trendBars(int count) {
        return IntStream.range(0, count).mapToObj(i -> bar(i, i + 1, i + 1.5, i + .5, i + 1, i + 100)).toList();
    }

    private TrainingSessionCandle bar(int idx, double open, double high, double low, double close, double volume) {
        return TrainingSessionCandle.builder().chartId(CHART_ID).idx(idx).t((long) idx)
                .o(open).h(high).l(low).c(close).v(volume).build();
    }
}
