package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.kis.dto.CandleDto;
import com.tradenova.market.service.MarketDataService;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.report.service.TrainingEventService;
import com.tradenova.symbol.repository.SymbolRepository;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import com.tradenova.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingSessionVisibleCandlesTest {

    @Mock private TrainingSessionRepository sessionRepo;
    @Mock private TrainingSessionChartRepository chartRepo;
    @Mock private SymbolRepository symbolRepository;
    @Mock private UserRepository userRepository;
    @Mock private MarketDataService marketDataService;
    @Mock private PaperAccountRepository paperAccountRepository;
    @Mock private TrainingSessionCandleRepository candleRepo;
    @Mock private TrainingEventService trainingEventService;
    @Mock private TrainingTradeRepository tradeRepository;
    @Mock private ReportDocumentRepository reportDocumentRepository;
    @Mock private TrainingEventRepository trainingEventRepository;
    @Mock private TrainingTradeService trainingTradeService;

    private TrainingSessionService service;

    @BeforeEach
    void setUp() {
        service = new TrainingSessionService(
                new ObjectMapper(), sessionRepo, chartRepo, symbolRepository, userRepository,
                marketDataService, paperAccountRepository, candleRepo, trainingEventService,
                tradeRepository, reportDocumentRepository, trainingEventRepository, trainingTradeService
        );
    }

    @Test
    void returnsOnlyVisibleCandlesForNewCompletedAndLegacyCharts() {
        assertVisibleRange(300, 199, 200);
        assertVisibleRange(300, 200, 201);
        assertVisibleRange(300, 299, 300);
        assertVisibleRange(100, 59, 60);
        assertVisibleRange(100, 60, 61);
    }

    private void assertVisibleRange(int bars, int progressIndex, int expectedSize) {
        long chartId = bars * 1_000L + progressIndex;
        TrainingSessionChart chart = TrainingSessionChart.builder()
                .id(chartId)
                .bars(bars)
                .progressIndex(progressIndex)
                .build();
        List<TrainingSessionCandle> visible = IntStream.range(0, expectedSize)
                .mapToObj(idx -> candle(chartId, idx))
                .toList();

        when(chartRepo.findByIdAndSession_User_Id(chartId, 7L)).thenReturn(Optional.of(chart));
        when(candleRepo.findAllByChartIdAndIdxLessThanEqualOrderByIdxAsc(chartId, progressIndex))
                .thenReturn(visible);

        List<CandleDto> result = service.getChartCandles(7L, chartId);

        assertThat(result).hasSize(expectedSize);
        assertThat(result).extracting(CandleDto::t).containsExactlyElementsOf(
                IntStream.range(0, expectedSize).asLongStream().boxed().toList()
        );
        verify(candleRepo).findAllByChartIdAndIdxLessThanEqualOrderByIdxAsc(chartId, progressIndex);
    }

    private TrainingSessionCandle candle(long chartId, int idx) {
        return TrainingSessionCandle.builder()
                .chartId(chartId).idx(idx).t((long) idx)
                .o(100.0).h(101.0).l(99.0).c(100.0).v(1_000.0)
                .build();
    }
}
