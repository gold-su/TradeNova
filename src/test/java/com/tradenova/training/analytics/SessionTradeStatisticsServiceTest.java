package com.tradenova.training.analytics;

import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionTradeStatisticsServiceTest {

    @Test
    void analyzesActiveAndInactiveCharts() {
        TrainingSessionRepository sessionRepository = mock(TrainingSessionRepository.class);
        TrainingSessionChartRepository chartRepository = mock(TrainingSessionChartRepository.class);
        TradeEpisodeAnalysisService episodeService = mock(TradeEpisodeAnalysisService.class);
        SessionTradeStatisticsCalculator calculator = new SessionTradeStatisticsCalculator();
        SessionTradeStatisticsService service = new SessionTradeStatisticsService(
                sessionRepository, chartRepository, episodeService, calculator
        );
        TrainingSession session = TrainingSession.builder().id(5L).build();
        TrainingSessionChart active = TrainingSessionChart.builder().id(10L).active(true).build();
        TrainingSessionChart inactive = TrainingSessionChart.builder().id(20L).active(false).build();
        TradeEpisodeAnalysisResult activeResult = new TradeEpisodeAnalysisResult(10L, List.of());
        TradeEpisodeAnalysisResult inactiveResult = new TradeEpisodeAnalysisResult(20L, List.of());

        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));
        when(chartRepository.findAllBySession_IdOrderByChartIndexAsc(5L))
                .thenReturn(List.of(active, inactive));
        when(episodeService.analyzeChart(10L)).thenReturn(activeResult);
        when(episodeService.analyzeChart(20L)).thenReturn(inactiveResult);
        SessionTradeStatistics result = service.analyzeSession(1L, 5L);

        assertEquals(0, result.totalEpisodeCount());
        verify(episodeService).analyzeChart(10L);
        verify(episodeService).analyzeChart(20L);
    }

    @Test
    void propagatesEpisodeDataQualityFailure() {
        TrainingSessionRepository sessionRepository = mock(TrainingSessionRepository.class);
        TrainingSessionChartRepository chartRepository = mock(TrainingSessionChartRepository.class);
        TradeEpisodeAnalysisService episodeService = mock(TradeEpisodeAnalysisService.class);
        SessionTradeStatisticsCalculator calculator = new SessionTradeStatisticsCalculator();
        SessionTradeStatisticsService service = new SessionTradeStatisticsService(
                sessionRepository, chartRepository, episodeService, calculator
        );
        TrainingSession session = TrainingSession.builder().id(5L).build();
        TrainingSessionChart chart = TrainingSessionChart.builder().id(10L).build();
        TradeEpisodeDataException failure = new TradeEpisodeDataException("oversell");

        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));
        when(chartRepository.findAllBySession_IdOrderByChartIndexAsc(5L)).thenReturn(List.of(chart));
        when(episodeService.analyzeChart(10L)).thenThrow(failure);

        TradeEpisodeDataException thrown = assertThrows(
                TradeEpisodeDataException.class,
                () -> service.analyzeSession(1L, 5L)
        );
        assertSame(failure, thrown);
    }
}
