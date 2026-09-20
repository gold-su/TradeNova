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
        GrowthOverviewResponse result = calculator.calculate("ALL", List.of(recent, older));

        assertEquals(300, result.totalXp());
        assertEquals(1, result.level());
        assertEquals(50.0, result.planSessionRate().rate());
        assertEquals(2, result.actionReasonRate().numerator());
        assertEquals(3, result.actionReasonRate().denominator());
        assertEquals(70.0, result.averageSessionAiScore());
        assertEquals(List.of(1L, 2L), result.scoreTrend().stream().map(point -> point.sessionId()).toList());
    }

    @Test
    void handlesNoDataWithoutInventingScoresOrRates() {
        GrowthOverviewResponse result = calculator.calculate("ALL", List.of());
        assertEquals(0, result.totalXp());
        assertEquals(1, result.level());
        assertEquals("Observer", result.levelTitle());
        assertEquals(0, result.planSessionRate().rate());
        assertNull(result.averageSessionAiScore());
        assertTrue(result.scoreTrend().isEmpty());
    }

    @Test
    void appliesProgressiveLevelTitlesWithoutProfitabilityInputs() {
        var sessions = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> new GrowthAnalyticsCalculator.SessionFact((long) index, Instant.EPOCH.plusSeconds(index), 0, 0, 0, false, false, null))
                .toList();
        GrowthOverviewResponse result = calculator.calculate("ALL", sessions);
        assertEquals(2000, result.totalXp());
        assertEquals(5, result.level());
        assertEquals("Process Builder", result.levelTitle());
    }
}
