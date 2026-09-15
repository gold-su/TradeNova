package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.report.dto.*;
import com.tradenova.report.entity.*;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionRiskComplianceEvidenceResolverTest {
    private final TrainingRiskRuleHistoryRepository repository = mock(TrainingRiskRuleHistoryRepository.class);
    private final SessionRiskComplianceEvidenceResolver resolver = new SessionRiskComplianceEvidenceResolver(repository);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runtimeStopIsFollowedDespiteNegativePnlAndGapBelowStop() {
        var evidence = resolve(104, 104, 100, "STOP_LOSS", true);
        assertEquals("FOLLOWED", evidence.compliance());
        assertEquals(0, new BigDecimal("100").compareTo(evidence.executedExitPercent()));
        assertTrue(evidence.stopLossConfigured());
        assertTrue(evidence.takeProfitConfigured());
        assertTrue(evidence.autoExitEnabledAtTrade());
        assertEquals(70L, evidence.riskRuleHistoryId());
        verify(repository).findAllByIdIn(Set.of(70L));
        verifyNoMoreInteractions(repository);
        String formatted = new SessionQualitativeEvidenceFormatter().format(
                new SessionQualitativeEvidenceContext(131L, List.of(), List.of(evidence)));
        assertTrue(formatted.contains("compliance=FOLLOWED"));
        assertTrue(formatted.contains("not overall plan quality"));
        assertTrue(formatted.contains("negative PnL"));
    }

    @Test
    void partialStopFollowsPlannedFiftyPercent() {
        var evidence = resolve(104, 52, 50, "STOP_LOSS", true);
        assertEquals("FOLLOWED", evidence.compliance());
        assertEquals(0, new BigDecimal("50").compareTo(evidence.executedExitPercent()));
        assertEquals(new BigDecimal("52"), evidence.plannedExitQty());
    }

    @Test
    void roundedPartialExitAndMinimumOneShareAreFollowed() {
        assertEquals("FOLLOWED", resolve(3, 1, 50, "STOP_LOSS", true).compliance());
        assertEquals("FOLLOWED", resolve(1, 1, 10, "STOP_LOSS", true).compliance());
    }

    @Test
    void takeProfitAlsoUsesItsPlannedPercent() {
        assertEquals("FOLLOWED", resolve(104, 52, 50, "TAKE_PROFIT", true).compliance());
    }

    @Test
    void ruleWithoutObservedTriggerDoesNotMeanViolation() {
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of(plan(100)));
        var evidence = resolver.resolve(context(List.of(248L)), List.of(trade(248L, TradeSide.BUY, 104)), List.of());
        assertEquals("UNKNOWN", evidence.get(0).compliance());
        assertNull(evidence.get(0).executionReason());
    }

    @Test
    void manualExitAloneDoesNotEstablishDeviation() {
        assertEquals("UNKNOWN", resolve(104, 104, 100, null, false).compliance());
    }

    @Test
    void provenTriggerWithManualExecutionIsDeviation() {
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of(plan(100)));
        var warning = event(Type.WARNING, mapper.createObjectNode().put("tradeId", 249L)
                .put("candleTime", 2000L).put("reason", "STOP_LOSS").put("qty", 104).put("executedPrice", 64500));
        var manual = event(Type.TRADE, payload(104, null, false));
        var evidence = resolver.resolve(context(List.of(248L, 249L)), trades(104, 104),
                List.of(manual, warning));
        assertEquals("NOT_FOLLOWED", evidence.get(0).compliance());
    }

    @Test
    void wrongExecutedQuantityIsDeviationOnlyWithProvenTrigger() {
        assertEquals("NOT_FOLLOWED", resolve(104, 52, 100, "STOP_LOSS", true).compliance());
    }

    @Test
    void malformedOrWrongChartEventCannotProveCompliance() {
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of(plan(100)));
        var wrong = event(Type.TRADE, payload(104, "STOP_LOSS", true));
        wrong.setChartId(606L);
        assertEquals("UNKNOWN", resolver.resolve(context(List.of(248L, 249L)), trades(104, 104), List.of(wrong))
                .get(0).compliance());
        var malformed = event(Type.TRADE, payload(104, "STOP_LOSS", true).put("riskRuleHistoryId", 71L));
        assertEquals("UNKNOWN", resolver.resolve(context(List.of(248L, 249L)), trades(104, 104), List.of(malformed))
                .get(0).compliance());
    }

    @Test
    void foreignHistoryAndDuplicateActionsRemainUnknown() {
        var foreign = TrainingRiskRuleHistory.builder().id(70L).userId(99L).build();
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of(foreign));
        var action = event(Type.TRADE, payload(104, "STOP_LOSS", true));
        assertEquals("UNKNOWN", resolver.resolve(context(List.of(248L, 249L)), trades(104, 104), List.of(action))
                .get(0).compliance());
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of(plan(100)));
        assertEquals("UNKNOWN", resolver.resolve(context(List.of(248L, 249L)), trades(104, 104), List.of(action, action))
                .get(0).compliance());
    }

    @Test
    void intermediatePartialExitUsesRemainingPositionAndOneBulkHistoryQuery() {
        when(repository.findAllByIdIn(Set.of(70L, 71L))).thenReturn(List.of(plan(50), plan(100, 71L)));
        TrainingTrade secondExit = trade(250L, TradeSide.SELL, 52);
        secondExit.setRiskRuleHistoryId(71L);
        var secondEvent = event(Type.TRADE, payload(52, "TAKE_PROFIT", true).put("tradeId", 250L)
                .put("riskRuleHistoryId", 71L));
        var evidence = resolver.resolve(context(List.of(248L, 249L, 250L)),
                List.of(trade(248L, TradeSide.BUY, 104), trade(249L, TradeSide.SELL, 52), secondExit),
                List.of(event(Type.TRADE, payload(52, "STOP_LOSS", true)), secondEvent));
        assertEquals(List.of("FOLLOWED", "FOLLOWED"), evidence.stream().map(RiskComplianceAiEvidence::compliance).toList());
        assertEquals(new BigDecimal("52"), evidence.get(1).plannedExitQty());
        assertEquals(50, evidence.get(0).plannedExitPercent());
        assertEquals(100, evidence.get(1).plannedExitPercent());
        verify(repository).findAllByIdIn(Set.of(70L, 71L));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void sessionResolverConnectsBackendEvidenceAlongsideAuthoredEvidence() {
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of(plan(100)));
        var qualitative = new SessionQualitativeEvidenceResolver(new TradeActionAiEvidenceResolver(),
                new ScenarioPlanAiEvidenceResolver(), resolver).resolve(context(List.of(248L, 249L)), List.of(),
                List.of(event(Type.TRADE, payload(104, "STOP_LOSS", true))), trades(104, 104));
        assertEquals("FOLLOWED", qualitative.riskCompliance().get(0).compliance());
        assertTrue(qualitative.charts().get(0).tradeActions().isEmpty());
    }

    @Test
    void userClaimsCannotOverrideSystemExecutionAndForcedExitsAreNotRiskTriggers() {
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of(plan(100)));
        var automatic = event(Type.TRADE, payload(104, "STOP_LOSS", true));
        var claim = event(Type.TRADE, payload(104, null, false));
        claim.setOrigin(EventOrigin.USER);
        assertEquals("FOLLOWED", resolver.resolve(context(List.of(248L, 249L)), trades(104, 104),
                List.of(automatic, claim)).get(0).compliance());
        assertEquals("UNKNOWN", resolver.resolve(context(List.of(248L, 249L)), trades(104, 104),
                List.of(claim)).get(0).compliance());
        assertEquals("NOT_APPLICABLE", resolve(104, 104, 100, "END_OF_CHART", true).compliance());
    }

    @Test
    void missingOrFutureHistoryNeverEstablishesCompliance() {
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of());
        var action = event(Type.TRADE, payload(104, "STOP_LOSS", true));
        assertEquals("UNKNOWN", resolver.resolve(context(List.of(248L, 249L)), trades(104, 104), List.of(action))
                .get(0).compliance());
        var future = TrainingRiskRuleHistory.builder().id(70L).userId(1L).sessionId(131L).accountId(2L)
                .chartId(605L).candleTime(3000L).stopLossPrice(new BigDecimal("66612"))
                .autoExitEnabled(true).stopLossExitPercent(100).build();
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of(future));
        assertEquals("UNKNOWN", resolver.resolve(context(List.of(248L, 249L)), trades(104, 104), List.of(action))
                .get(0).compliance());
    }

    @Test
    void terminalLiquidationsAreExplicitAndNotRiskComplianceCases() {
        var chartEnd = resolve(104, 104, 100, "END_OF_CHART", true);
        assertEquals("END_OF_CHART", chartEnd.executionReason());
        assertEquals("NOT_APPLICABLE", chartEnd.compliance());
        assertTrue(chartEnd.basis().contains("terminal lifecycle liquidation"));

        var sessionEnd = resolve(104, 104, 100, "END_OF_SESSION", true);
        assertEquals("END_OF_SESSION", sessionEnd.executionReason());
        assertEquals("NOT_APPLICABLE", sessionEnd.compliance());
    }

    @Test
    void chartCountsAndOutcomeLabelsAreExplicitlyNeutralInPromptAndFormatter() {
        var builder = new PromptBuilder(new SessionAiDeterministicContextFormatter());
        String system = builder.buildSessionSystemPrompt();
        String formatted = new SessionQualitativeEvidenceFormatter().format(new SessionQualitativeEvidenceContext(131L, List.of()));
        assertTrue(system.contains("거래가 없고 explicit user-authored Scenario/Reason이 없는 chart는 평가에서 제외"));
        assertTrue(system.contains("미거래 이유 부재 자체를 warnings, recommendations, nextTrainingFocus"));
        assertTrue(formatted.contains("NO-TRADE EXCLUSION POLICY"));
        assertTrue(formatted.contains("Never request a no-trade explanation or plan"));
        assertTrue(system.contains("profit/loss/win/loss alone must not determine process quality"));
        assertTrue(system.contains("avoid \"win\"/\"loss\" as quality labels"));
    }

    private RiskComplianceAiEvidence resolve(int buy, int sell, int percent, String reason, boolean automatic) {
        when(repository.findAllByIdIn(Set.of(70L))).thenReturn(List.of(plan(percent)));
        return resolver.resolve(context(List.of(248L, 249L)), trades(buy, sell),
                List.of(event(Type.TRADE, payload(sell, reason, automatic)))).get(0);
    }

    private TrainingRiskRuleHistory plan(int percent) {
        return plan(percent, 70L);
    }

    private TrainingRiskRuleHistory plan(int percent, Long id) {
        return TrainingRiskRuleHistory.builder().id(id).userId(1L).sessionId(131L).accountId(2L).chartId(605L)
                .stopLossPrice(new BigDecimal("66612")).takeProfitPrice(new BigDecimal("86376"))
                .stopLossExitPercent(percent).takeProfitExitPercent(percent).autoExitEnabled(true).candleTime(1000L).build();
    }

    private List<TrainingTrade> trades(int buy, int sell) {
        return List.of(trade(248L, TradeSide.BUY, buy), trade(249L, TradeSide.SELL, sell));
    }

    private TrainingTrade trade(Long id, TradeSide side, int qty) {
        return TrainingTrade.builder().id(id).chartId(605L).accountId(2L).riskRuleHistoryId(70L).side(side)
                .qty(BigDecimal.valueOf(qty)).price(BigDecimal.valueOf(side == TradeSide.BUY ? 73200 : 64500))
                .candleTime(side == TradeSide.BUY ? 1000L : 2000L).build();
    }

    private ObjectNode payload(int qty, String reason, boolean automatic) {
        return mapper.createObjectNode().put("tradeId", 249L).put("side", "SELL").put("riskRuleHistoryId", 70L)
                .put("qty", qty).put("executedPrice", 64500).put("candleTime", 2000L)
                .put("autoExit", automatic).put("autoExitReason", reason);
    }

    private TrainingEvent event(Type type, ObjectNode payload) {
        return TrainingEvent.builder().id(900L).userId(1L).chartId(605L).origin(EventOrigin.SYSTEM)
                .type(type).payloadJson(payload).build();
    }

    private SessionAiDeterministicContext context(List<Long> ids) {
        var episode = new TradeEpisodeAiContext(1, List.of(248L), ids.stream().filter(id -> id != 248L).toList(),
                ids, 1000L, 2000L, 1, ids.size() - 1, new BigDecimal("104"), new BigDecimal("104"),
                new BigDecimal("73200"), new BigDecimal("64500"), new BigDecimal("-904800"),
                new BigDecimal("-11.8852"), 1, true, BigDecimal.ZERO, 70L, 70L);
        var chart = new ChartAiDeterministicContext(605L, 0, 20L, "TEST", "Test", "ETC", null, null,
                10, 9, "COMPLETED", true, false, ids.size(), 1, true, List.of(episode));
        return new SessionAiDeterministicContext(131L, 1L, 2L, "RANDOM", "COMPLETED", 4, 4, 4, 1,
                ids.size(), null, List.of(chart));
    }
}
