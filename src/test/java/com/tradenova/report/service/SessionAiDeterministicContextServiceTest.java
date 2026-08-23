package com.tradenova.report.service;

import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.report.dto.ChartAiDeterministicContext;
import com.tradenova.report.dto.SessionAiDeterministicContext;
import com.tradenova.report.dto.TradeEpisodeAiContext;
import com.tradenova.symbol.dto.SymbolSector;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.analytics.SessionTradeStatisticsCalculator;
import com.tradenova.training.analytics.TradeEpisode;
import com.tradenova.training.analytics.TradeEpisodeAnalysisResult;
import com.tradenova.training.analytics.TradeEpisodeAnalysisService;
import com.tradenova.training.analytics.TradeEpisodeDataException;
import com.tradenova.training.entity.TrainingChartStatus;
import com.tradenova.training.entity.TrainingMode;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionAiDeterministicContextServiceTest {

    @Test
    void buildsStatisticsAndEpisodeEvidenceIncludingInactiveChart() {
        TrainingSessionRepository sessionRepository = mock(TrainingSessionRepository.class);
        TrainingSessionChartRepository chartRepository = mock(TrainingSessionChartRepository.class);
        TradeEpisodeAnalysisService episodeService = mock(TradeEpisodeAnalysisService.class);
        SessionAiDeterministicContextService service = new SessionAiDeterministicContextService(
                sessionRepository,
                chartRepository,
                episodeService,
                new SessionTradeStatisticsCalculator()
        );
        TrainingSession session = TrainingSession.builder()
                .id(5L)
                .account(PaperAccount.builder().id(50L).build())
                .mode(TrainingMode.RANDOM)
                .status(TrainingStatus.IN_PROGRESS)
                .build();
        TrainingSessionChart active = chart(10L, session, true, false, TrainingChartStatus.IN_PROGRESS);
        TrainingSessionChart inactive = chart(20L, session, false, true, TrainingChartStatus.COMPLETED);
        TradeEpisode closedEpisode = episode(20L);

        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));
        when(chartRepository.findAllBySession_IdOrderByChartIndexAsc(5L))
                .thenReturn(List.of(active, inactive));
        when(episodeService.analyzeChart(10L))
                .thenReturn(new TradeEpisodeAnalysisResult(10L, List.of()));
        when(episodeService.analyzeChart(20L))
                .thenReturn(new TradeEpisodeAnalysisResult(20L, List.of(closedEpisode)));

        SessionAiDeterministicContext context = service.build(1L, 5L);

        assertEquals(5L, context.sessionId());
        assertEquals(50L, context.accountId());
        assertEquals(2, context.totalChartCount());
        assertEquals(1, context.activeChartCount());
        assertEquals(1, context.completedChartCount());
        assertEquals(1, context.tradedChartCount());
        assertEquals(2, context.totalTradeCount());
        assertEquals(1, context.tradeStatistics().closedEpisodeCount());
        decimalEquals("100", context.tradeStatistics().totalRealizedPnl());

        ChartAiDeterministicContext inactiveContext = context.charts().get(1);
        assertEquals(20L, inactiveContext.chartId());
        assertFalse(inactiveContext.active());
        assertTrue(inactiveContext.refreshed());
        assertTrue(inactiveContext.traded());
        TradeEpisodeAiContext episodeContext = inactiveContext.episodes().get(0);
        assertEquals(List.of(101L), episodeContext.entryTradeIds());
        assertEquals(List.of(102L), episodeContext.exitTradeIds());
        assertEquals(700L, episodeContext.firstEntryRiskRuleHistoryId());
        assertEquals(800L, episodeContext.lastExitRiskRuleHistoryId());
        decimalEquals("100", episodeContext.realizedPnl());
        decimalEquals("10", episodeContext.returnPct());
        assertEquals(5, episodeContext.holdingBars());
        verify(episodeService).analyzeChart(10L);
        verify(episodeService).analyzeChart(20L);
    }

    @Test
    void propagatesEpisodeDataQualityFailureWithoutBuildingPartialContext() {
        TrainingSessionRepository sessionRepository = mock(TrainingSessionRepository.class);
        TrainingSessionChartRepository chartRepository = mock(TrainingSessionChartRepository.class);
        TradeEpisodeAnalysisService episodeService = mock(TradeEpisodeAnalysisService.class);
        SessionAiDeterministicContextService service = new SessionAiDeterministicContextService(
                sessionRepository,
                chartRepository,
                episodeService,
                new SessionTradeStatisticsCalculator()
        );
        TrainingSession session = TrainingSession.builder()
                .id(5L)
                .account(PaperAccount.builder().id(50L).build())
                .mode(TrainingMode.RANDOM)
                .status(TrainingStatus.IN_PROGRESS)
                .build();
        TrainingSessionChart chart = chart(10L, session, true, false, TrainingChartStatus.IN_PROGRESS);
        TradeEpisodeDataException failure = new TradeEpisodeDataException("oversell");

        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));
        when(chartRepository.findAllBySession_IdOrderByChartIndexAsc(5L)).thenReturn(List.of(chart));
        when(episodeService.analyzeChart(10L)).thenThrow(failure);

        TradeEpisodeDataException thrown = assertThrows(
                TradeEpisodeDataException.class,
                () -> service.build(1L, 5L)
        );
        assertSame(failure, thrown);
    }

    private TrainingSessionChart chart(
            Long id,
            TrainingSession session,
            boolean active,
            boolean refreshed,
            TrainingChartStatus status
    ) {
        return TrainingSessionChart.builder()
                .id(id)
                .session(session)
                .chartIndex(id.intValue())
                .symbol(Symbol.builder()
                        .id(id + 1_000)
                        .ticker("T" + id)
                        .name("Symbol " + id)
                        .trainingSector(SymbolSector.ETC)
                        .build())
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 6, 1))
                .bars(120)
                .progressIndex(119)
                .status(status)
                .active(active)
                .refreshed(refreshed)
                .build();
    }

    private TradeEpisode episode(Long chartId) {
        return new TradeEpisode(
                1,
                chartId,
                50L,
                chartId + 1_000,
                List.of(101L),
                List.of(102L),
                List.of(101L, 102L),
                1_000L,
                2_000L,
                1,
                1,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(110),
                BigDecimal.valueOf(100),
                BigDecimal.TEN,
                5,
                true,
                BigDecimal.ZERO,
                700L,
                800L
        );
    }

    private void decimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
