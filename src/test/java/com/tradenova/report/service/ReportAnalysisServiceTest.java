package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.AiAnalysisResponse;
import com.tradenova.report.entity.EventOrigin;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingTrade;
import com.tradenova.training.entity.TrainingRiskRuleHistory;
import com.tradenova.training.entity.TradeSide;
import com.tradenova.training.analytics.DecisionTechnicalContext;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import com.tradenova.training.analytics.DecisionTechnicalContextService;
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
import java.time.Instant;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Mock private TrainingRiskRuleHistoryRepository trainingRiskRuleHistoryRepository;
    @Mock private TrainingEventService trainingEventService;
    @Mock private PaperPositionRepository paperPositionRepository;
    @Mock private TrainingEventRepository trainingEventRepository;
    @Mock private DecisionTechnicalContextService technicalContextService;
    @Spy private TradeActionAiEvidenceResolver tradeActionEvidenceResolver = new TradeActionAiEvidenceResolver();
    @Spy private ScenarioPlanAiEvidenceResolver scenarioPlanEvidenceResolver = new ScenarioPlanAiEvidenceResolver();
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private ReportAnalysisService service;

    private TrainingSessionChart chart;

    @BeforeEach
    void setUp() {
        PaperAccount account = PaperAccount.builder()
                .id(20L)
                .cashBalance(BigDecimal.valueOf(1_000_000))
                .build();
        TrainingSession session = TrainingSession.builder().id(85L).account(account).build();
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
        when(technicalContextService.boundedOhlcv(any(), any())).thenReturn(List.of());
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

    @Test
    void entryContextUsesResolvedLatestBuyIndexInsteadOfCurrentProgress() {
        when(candleRepository.findTop30ByChartIdAndIdxLessThanEqualOrderByIdxDesc(CHART_ID, 59))
                .thenReturn(descendingCandles(59, 30));
        TrainingTrade buy = TrainingTrade.builder().chartId(CHART_ID).side(TradeSide.BUY).candleTime(22L).build();
        when(tradeRepository.findTopByChartIdAndSideOrderByIdDesc(CHART_ID, TradeSide.BUY)).thenReturn(Optional.of(buy));
        when(candleRepository.findByChartIdAndT(CHART_ID, 22L)).thenReturn(Optional.of(descendingCandles(22, 22).get(0)));
        DecisionTechnicalContext current = contextAt(59);
        DecisionTechnicalContext entry = contextAt(22);
        when(technicalContextService.calculate(CHART_ID, 59)).thenReturn(current);
        when(technicalContextService.calculate(CHART_ID, 22)).thenReturn(entry);

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        AiAnalysisRequest request = capturedAiRequest();
        assertEquals(current, request.currentVisibleTechnicalContext());
        assertEquals(entry, request.entryDecisionTechnicalContext());
        verify(technicalContextService).calculate(CHART_ID, 59);
        verify(technicalContextService).calculate(CHART_ID, 22);
        verify(technicalContextService).boundedOhlcv(CHART_ID, 22);
    }

    @Test
    void noBuyStillProvidesCurrentContextAndNullEntryContext() {
        when(candleRepository.findTop30ByChartIdAndIdxLessThanEqualOrderByIdxDesc(CHART_ID, 59))
                .thenReturn(descendingCandles(59, 30));
        DecisionTechnicalContext current = contextAt(59);
        when(technicalContextService.calculate(CHART_ID, 59)).thenReturn(current);

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        AiAnalysisRequest request = capturedAiRequest();
        assertEquals(current, request.currentVisibleTechnicalContext());
        assertEquals(null, request.entryDecisionTechnicalContext());
    }

    @Test
    void attachesEntryBuyAndLatestSellReasonsByExactTradeId() {
        visibleCandles();
        TrainingTrade buy = trade(101L, TradeSide.BUY, 22L);
        TrainingTrade sell = trade(102L, TradeSide.SELL, 30L);
        when(tradeRepository.findTopByChartIdAndSideOrderByIdDesc(CHART_ID, TradeSide.BUY))
                .thenReturn(Optional.of(buy));
        when(tradeRepository.findTopByChartIdOrderByIdDesc(CHART_ID)).thenReturn(Optional.of(sell));
        when(candleRepository.findByChartIdAndT(CHART_ID, 22L))
                .thenReturn(Optional.of(descendingCandles(22, 22).get(0)));
        TrainingEvent sellEvent = tradeEvent(102L, CHART_ID, "SELL", "exit now");
        TrainingEvent buyEvent = tradeEvent(101L, CHART_ID, "BUY", "entry now");
        when(trainingEventRepository.findAllByUserIdAndChartIdAndTypeOrderByIdDesc(USER_ID, CHART_ID, Type.TRADE))
                .thenReturn(List.of(sellEvent, buyEvent));

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        AiAnalysisRequest request = capturedAiRequest();
        assertEquals(101L, request.entryActionEvidence().tradeId());
        assertEquals("entry now", request.entryActionEvidence().reasons().get(0).entryReason());
        assertEquals(102L, request.latestActionEvidence().tradeId());
        assertEquals("SELL", request.latestActionEvidence().side());
    }

    @Test
    void doesNotAttachDifferentTradeIdOrChartAndIgnoresLegacyPayload() {
        visibleCandles();
        TrainingTrade buy = trade(101L, TradeSide.BUY, 22L);
        when(tradeRepository.findTopByChartIdAndSideOrderByIdDesc(CHART_ID, TradeSide.BUY))
                .thenReturn(Optional.of(buy));
        when(tradeRepository.findTopByChartIdOrderByIdDesc(CHART_ID)).thenReturn(Optional.of(buy));
        when(candleRepository.findByChartIdAndT(CHART_ID, 22L))
                .thenReturn(Optional.of(descendingCandles(22, 22).get(0)));
        TrainingEvent legacy = TrainingEvent.builder().id(3L).userId(USER_ID).chartId(CHART_ID)
                .type(Type.TRADE).origin(EventOrigin.USER).summary("legacy")
                .payloadJson(objectMapper.createObjectNode().put("tradeId", 101L).put("side", "BUY")).build();
        TrainingEvent unrelatedTrade = tradeEvent(999L, CHART_ID, "BUY", "unrelated");
        TrainingEvent wrongChartTrade = tradeEvent(101L, 999L, "BUY", "wrong chart");
        when(trainingEventRepository.findAllByUserIdAndChartIdAndTypeOrderByIdDesc(USER_ID, CHART_ID, Type.TRADE))
                .thenReturn(List.of(unrelatedTrade, wrongChartTrade, legacy));

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        assertNull(capturedAiRequest().entryActionEvidence());
    }

    @Test
    void linksEntryBuyToExactExplicitScenarioRatherThanLatestSnapshot() {
        visibleCandles();
        TrainingTrade buy = trade(101L, TradeSide.BUY, 22L);
        when(tradeRepository.findTopByChartIdAndSideOrderByIdDesc(CHART_ID, TradeSide.BUY)).thenReturn(Optional.of(buy));
        when(tradeRepository.findTopByChartIdOrderByIdDesc(CHART_ID)).thenReturn(Optional.of(buy));
        when(candleRepository.findByChartIdAndT(CHART_ID, 22L))
                .thenReturn(Optional.of(descendingCandles(22, 22).get(0)));
        TrainingEvent buyEvent = tradeEvent(101L, CHART_ID, "BUY", "scenario matched");
        ((ObjectNode) buyEvent.getPayloadJson()).put("reasonMode", "SCENARIO").put("scenarioSnapshotId", 55L);
        ReportDocument selected = scenario(55L, 1, "selected");
        ReportDocument latestContext = scenario(56L, 2, "newer context only");
        when(reportDocumentRepository.findTopByUserIdAndChartIdAndKindOrderByVersionDesc(
                USER_ID, CHART_ID, ReportKind.SNAPSHOT)).thenReturn(Optional.of(latestContext));
        when(trainingEventRepository.findAllByUserIdAndChartIdAndTypeOrderByIdDesc(USER_ID, CHART_ID, Type.TRADE))
                .thenReturn(List.of(buyEvent));
        when(reportDocumentRepository.findAllById(List.of(55L))).thenReturn(List.of(selected));

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        assertEquals(55L, capturedAiRequest().entryActionEvidence().scenarioPlan().snapshotId());
        assertEquals("selected", capturedAiRequest().entryActionEvidence().scenarioPlan().thesis());
        verify(reportDocumentRepository).findAllById(List.of(55L));
    }

    @Test
    void completedChartSeparatesHistoricalBuyAndTerminalExitFromFinalPosition() {
        chart.setProgressIndex(99);
        when(candleRepository.findTop30ByChartIdAndIdxLessThanEqualOrderByIdxDesc(CHART_ID, 99))
                .thenReturn(descendingCandles(99, 70));
        TrainingTrade buy = trade(238L, TradeSide.BUY, 22L);
        buy.setRiskRuleHistoryId(29L);
        TrainingTrade sell = trade(239L, TradeSide.SELL, 99L);
        sell.setRiskRuleHistoryId(29L);
        when(tradeRepository.findTopByChartIdAndSideOrderByIdDesc(CHART_ID, TradeSide.BUY)).thenReturn(Optional.of(buy));
        when(tradeRepository.findTopByChartIdOrderByIdDesc(CHART_ID)).thenReturn(Optional.of(sell));
        when(candleRepository.findByChartIdAndT(CHART_ID, 22L)).thenReturn(Optional.of(descendingCandles(22, 22).get(0)));
        when(trainingRiskRuleHistoryRepository.findById(29L)).thenReturn(Optional.of(history(true)));
        TrainingEvent terminalEvent = terminalEvent(sell, "END_OF_CHART");
        when(trainingEventRepository.findAllByUserIdAndChartIdAndTypeOrderByIdDesc(USER_ID, CHART_ID, Type.TRADE))
                .thenReturn(List.of(terminalEvent));

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        AiAnalysisRequest request = capturedAiRequest();
        assertTrue(request.lifecycleEvidence().historicalBuyExecuted());
        assertEquals(238L, request.lifecycleEvidence().latestBuyTradeId());
        assertEquals("SELL", request.lifecycleEvidence().latestTradeSide());
        assertTrue(request.lifecycleEvidence().finalPositionClosed());
        assertEquals("END_OF_CHART", request.lifecycleEvidence().terminalLiquidationReason());
        assertEquals(239L, request.lifecycleEvidence().terminalLiquidationTradeId());
        assertTrue(request.lifecycleEvidence().historicalAutoExitEnabled());
        assertEquals(Boolean.FALSE, request.lifecycleEvidence().currentAutoExitEnabled());

        String prompt = new PromptBuilder(new SessionAiDeterministicContextFormatter()).buildUserPrompt(request);
        assertTrue(prompt.contains("historicalBuyExecuted=true"));
        assertTrue(prompt.contains("terminalLiquidationReason=END_OF_CHART"));
        assertTrue(prompt.contains("CURRENT/FINAL STATE (entry-time state가 아님)"));
        assertTrue(prompt.contains("CURRENT RISK RULE STATE (historical trade-time plan이 아님)"));
    }

    @Test
    void endOfSessionIsAlsoExplicitTerminalLifecycleEvidence() {
        visibleCandles();
        TrainingTrade buy = trade(238L, TradeSide.BUY, 22L);
        TrainingTrade sell = trade(239L, TradeSide.SELL, 59L);
        when(tradeRepository.findTopByChartIdAndSideOrderByIdDesc(CHART_ID, TradeSide.BUY)).thenReturn(Optional.of(buy));
        when(tradeRepository.findTopByChartIdOrderByIdDesc(CHART_ID)).thenReturn(Optional.of(sell));
        when(candleRepository.findByChartIdAndT(CHART_ID, 22L)).thenReturn(Optional.of(descendingCandles(22, 22).get(0)));
        TrainingEvent terminalEvent = terminalEvent(sell, "END_OF_SESSION");
        when(trainingEventRepository.findAllByUserIdAndChartIdAndTypeOrderByIdDesc(USER_ID, CHART_ID, Type.TRADE))
                .thenReturn(List.of(terminalEvent));

        service.analyzeLatestSnapshot(USER_ID, CHART_ID);

        assertEquals("END_OF_SESSION", capturedAiRequest().lifecycleEvidence().terminalLiquidationReason());
    }

    private TrainingRiskRuleHistory history(boolean enabled) {
        return TrainingRiskRuleHistory.builder().id(29L).userId(USER_ID).sessionId(85L)
                .chartId(CHART_ID).accountId(20L).autoExitEnabled(enabled).candleTime(20L).build();
    }

    private TrainingEvent terminalEvent(TrainingTrade trade, String reason) {
        ObjectNode payload = objectMapper.createObjectNode().put("tradeId", trade.getId()).put("side", "SELL")
                .put("qty", trade.getQty()).put("executedPrice", trade.getPrice())
                .put("candleTime", trade.getCandleTime()).put("autoExit", true).put("autoExitReason", reason);
        return TrainingEvent.builder().id(2000L).userId(USER_ID).chartId(CHART_ID).type(Type.TRADE)
                .origin(EventOrigin.SYSTEM).payloadJson(payload).build();
    }

    private ReportDocument scenario(Long id, int version, String thesis) {
        ObjectNode content = objectMapper.createObjectNode().put("thesis", thesis);
        content.putArray("tags").add("SCENARIO");
        return ReportDocument.builder().id(id).userId(USER_ID).chartId(CHART_ID).kind(ReportKind.SNAPSHOT)
                .version(version).contentJson(content).createdAt(Instant.EPOCH).build();
    }

    private void visibleCandles() {
        when(candleRepository.findTop30ByChartIdAndIdxLessThanEqualOrderByIdxDesc(CHART_ID, 59))
                .thenReturn(descendingCandles(59, 30));
    }

    private TrainingTrade trade(Long id, TradeSide side, Long candleTime) {
        return TrainingTrade.builder().id(id).chartId(CHART_ID).side(side).candleTime(candleTime)
                .price(BigDecimal.TEN).qty(BigDecimal.ONE).build();
    }

    private TrainingEvent tradeEvent(Long tradeId, Long chartId, String side, String reason) {
        ObjectNode payload = objectMapper.createObjectNode().put("tradeId", tradeId).put("side", side)
                .put("qty", 1).put("price", 10).put("candleTime", 22L);
        payload.putArray("reasons").addObject().put("title", "reason").put("entryReason", reason)
                .put("riskNote", "risk").put("createdAt", "2026-01-01T00:00:00Z");
        return TrainingEvent.builder().id(tradeId + 1000).userId(USER_ID).chartId(chartId)
                .type(Type.TRADE).origin(EventOrigin.USER).summary("trade").payloadJson(payload)
                .createdAt(Instant.EPOCH).build();
    }

    private DecisionTechnicalContext contextAt(int index) {
        return new DecisionTechnicalContext(CHART_ID, index, (long) index, index + 1, (double) index,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(), List.of());
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
