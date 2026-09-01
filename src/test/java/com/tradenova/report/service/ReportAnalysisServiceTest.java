package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.AiAnalysisResponse;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportAnalysisServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CHART_ID = 10L;

    @Mock private ReportDocumentRepository reportDocumentRepository;
    @Mock private TrainingSessionChartRepository chartRepository;
    @Mock private TrainingSessionCandleRepository candleRepository;
    @Mock private TrainingTradeRepository tradeRepository;
    @Mock private AiAnalysisService aiAnalysisService;
    @Mock private TrainingRiskRuleRepository trainingRiskRuleRepository;
    @Mock private TrainingEventService trainingEventService;
    @Mock private PaperPositionRepository paperPositionRepository;
    @Mock private TrainingEventRepository trainingEventRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private ReportAnalysisService service;

    private TrainingSessionChart chart;

    @BeforeEach
    void setUp() {
        PaperAccount account = PaperAccount.builder()
                .id(20L)
                .cashBalance(BigDecimal.valueOf(1_000_000))
                .build();
        TrainingSession session = TrainingSession.builder().account(account).build();
        Symbol symbol = Symbol.builder().id(30L).build();
        chart = TrainingSessionChart.builder()
                .id(CHART_ID)
                .session(session)
                .symbol(symbol)
                .bars(100)
                .progressIndex(59)
                .build();

        when(chartRepository.findByIdAndSession_User_Id(CHART_ID, USER_ID))
                .thenReturn(Optional.of(chart));
        when(trainingEventRepository.findAllByUserIdAndChartIdAndTypeOrderByIdDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(aiAnalysisService.analyze(any()))
                .thenReturn(new AiAnalysisResponse(80, "summary", List.of(), List.of()));
    }

    @Test
    void excludesFutureCandlesAndUsesLatestThirtyVisibleCandles() {
        List<TrainingSessionCandle> visible = descendingCandles(59, 30);
        when(candleRepository.findTop30ByChartIdAndIdxLessThanEqualOrderByIdxDesc(CHART_ID, 59))
                .thenReturn(visible);

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        verify(candleRepository)
                .findTop30ByChartIdAndIdxLessThanEqualOrderByIdxDesc(CHART_ID, 59);
        AiAnalysisRequest request = capturedAiRequest();
        assertEquals(30, request.closes().size());
        assertEquals(59.0, request.closes().get(0));
        assertEquals(30.0, request.closes().get(29));
        assertTrue(request.closes().stream().allMatch(close -> close <= 59));
    }

    @Test
    void usesOnlyAvailableVisibleCandlesWhenFewerThanThirtyExist() {
        chart.setProgressIndex(20);
        when(candleRepository.findTop30ByChartIdAndIdxLessThanEqualOrderByIdxDesc(CHART_ID, 20))
                .thenReturn(descendingCandles(20, 0));

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        AiAnalysisRequest request = capturedAiRequest();
        assertEquals(21, request.closes().size());
        assertEquals(20.0, request.closes().get(0));
        assertEquals(0.0, request.closes().get(20));
    }

    @Test
    void completedChartKeepsUsingLastThirtyCandles() {
        chart.setProgressIndex(99);
        when(candleRepository.findTop30ByChartIdAndIdxLessThanEqualOrderByIdxDesc(CHART_ID, 99))
                .thenReturn(descendingCandles(99, 70));

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        AiAnalysisRequest request = capturedAiRequest();
        assertEquals(30, request.closes().size());
        assertEquals(99.0, request.closes().get(0));
        assertEquals(70.0, request.closes().get(29));
    }

    private AiAnalysisRequest capturedAiRequest() {
        ArgumentCaptor<AiAnalysisRequest> captor = ArgumentCaptor.forClass(AiAnalysisRequest.class);
        verify(aiAnalysisService).analyze(captor.capture());
        return captor.getValue();
    }

    private List<TrainingSessionCandle> descendingCandles(int fromInclusive, int toInclusive) {
        return IntStream.iterate(fromInclusive, idx -> idx >= toInclusive, idx -> idx - 1)
                .mapToObj(idx -> TrainingSessionCandle.builder()
                        .chartId(CHART_ID)
                        .idx(idx)
                        .t((long) idx)
                        .o((double) idx)
                        .h((double) idx)
                        .l((double) idx)
                        .c((double) idx)
                        .v((double) idx)
                        .build())
                .toList();
    }
}
