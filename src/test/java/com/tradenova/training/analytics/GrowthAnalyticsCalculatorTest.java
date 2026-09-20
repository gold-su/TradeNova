package com.tradenova.training.analytics;

import com.tradenova.training.dto.GrowthOverviewResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrowthAnalyticsCalculatorTest {
    private final GrowthAnalyticsCalculator calculator = new GrowthAnalyticsCalculator();

    @Test
    void calculatesBehaviorOnlyXpLevelRatesAndChronologicalTrend() {
        var recent = new GrowthAnalyticsCalculator.SessionFact(2L, Instant.parse("2026-02-02T00:00:00Z"), 3, 2, 1, true, true, 80);
        var older = new GrowthAnalyticsCalculator.SessionFact(1L, Instant.parse("2026-01-01T00:00:00Z"), 1, 1, 1, false, false, 60);
        GrowthOverviewResponse result = calculator.calculate("ALL", null, List.of(recent, older), List.of(recent, older));

        assertEquals(300, result.lifetime().totalXp());
        assertEquals(1, result.lifetime().level());
        assertEquals(50.0, result.period().planSessionRate().rate());
        assertEquals(2, result.period().actionReasonRate().numerator());
        assertEquals(3, result.period().actionReasonRate().denominator());
        assertEquals(70.0, result.period().averageSessionAiScore());
        assertEquals(List.of(1L, 2L), result.period().scoreTrend().stream().map(point -> point.sessionId()).toList());
    }

    @Test
    void handlesNoDataWithoutInventingScoresOrRates() {
        GrowthOverviewResponse result = calculator.calculate("ALL", null, List.of(), List.of());
        assertEquals(0, result.lifetime().totalXp());
        assertEquals(1, result.lifetime().level());
        assertEquals("Observer", result.lifetime().levelTitle());
        assertEquals(0, result.period().planSessionRate().rate());
        assertNull(result.period().averageSessionAiScore());
        assertTrue(result.period().scoreTrend().isEmpty());
    }

    @Test
    void appliesProgressiveLevelTitlesWithoutProfitabilityInputs() {
        var sessions = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> new GrowthAnalyticsCalculator.SessionFact((long) index, Instant.EPOCH.plusSeconds(index), 0, 0, 0, false, false, null))
                .toList();
        GrowthOverviewResponse result = calculator.calculate("ALL", null, sessions, sessions);
        assertEquals(2000, result.lifetime().totalXp());
        assertEquals(5, result.lifetime().level());
        assertEquals("Process Builder", result.lifetime().levelTitle());
    }

    @Test
    void keepsLifetimeLevelStableWhilePeriodMetricsChange() {
        var first = new GrowthAnalyticsCalculator.SessionFact(1L, Instant.EPOCH, 1, 1, 1, true, true, 80);
        var second = new GrowthAnalyticsCalculator.SessionFact(2L, Instant.EPOCH.plusSeconds(1), 9, 2, 0, false, false, null);
        var lifetime = List.of(first, second);
        GrowthOverviewResponse lastOne = calculator.calculate("LAST_10", 10, lifetime, List.of(second));
        GrowthOverviewResponse all = calculator.calculate("ALL", null, lifetime, lifetime);

        assertEquals(all.lifetime(), lastOne.lifetime());
        assertNotEquals(all.period().totalTrades(), lastOne.period().totalTrades());
        assertNotEquals(all.period().planSessionRate(), lastOne.period().planSessionRate());
        assertNotEquals(all.period().scoreTrend(), lastOne.period().scoreTrend());
    }
}
