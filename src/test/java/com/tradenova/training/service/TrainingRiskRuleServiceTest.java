package com.tradenova.training.service;

import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.dto.AutoExitReason;
import com.tradenova.training.dto.RiskRuleResponse;
import com.tradenova.training.dto.RiskRuleUpsertRequest;
import com.tradenova.training.entity.TrainingChartStatus;
import com.tradenova.training.entity.TrainingRiskRule;
import com.tradenova.training.entity.TrainingRiskRuleHistory;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingRiskRuleServiceTest {

    @Mock private TrainingSessionChartRepository chartRepo;
    @Mock private TrainingRiskRuleRepository riskRepo;
    @Mock private TrainingRiskRuleHistoryRepository historyRepo;
    @Mock private TrainingSessionCandleRepository candleRepo;

    private TrainingRiskRuleService service;

    @BeforeEach
    void setUp() {
        service = new TrainingRiskRuleService(chartRepo, riskRepo, historyRepo, candleRepo);
    }

    @Test
    void everyUpsertAppendsAnImmutableSnapshotWhileLatestRuleKeepsCurrentValues() {
        TrainingSessionChart chart = chart();
        TrainingSessionCandle candle = candle();

        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(chart));
        when(candleRepo.findByChartIdAndIdx(1L, 4)).thenReturn(Optional.of(candle));
        when(riskRepo.findByChartId(1L))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(currentRule));
        when(riskRepo.save(any(TrainingRiskRule.class))).thenAnswer(invocation -> {
            TrainingRiskRule rule = invocation.getArgument(0);
            if (rule.getId() == null) {
                rule.setId(50L);
            }
            currentRule = rule;
            return rule;
        });
        when(historyRepo.save(any(TrainingRiskRuleHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(7L, 1L, request("90.00", "120.00", true));
        currentRule.setStopLossConsumed(true);
        currentRule.setTakeProfitConsumed(true);
        service.upsert(7L, 1L, request("85.00", "125.00", true));
        RiskRuleResponse latest = service.upsert(7L, 1L, request("85.00", "125.00", false));

        ArgumentCaptor<TrainingRiskRuleHistory> captor =
                ArgumentCaptor.forClass(TrainingRiskRuleHistory.class);
        verify(historyRepo, times(3)).save(captor.capture());
        List<TrainingRiskRuleHistory> history = captor.getAllValues();

        assertThat(history).hasSize(3);
        assertHistory(history.get(0), "90.00", "120.00", true);
        assertHistory(history.get(1), "85.00", "125.00", true);
        assertHistory(history.get(2), "85.00", "125.00", false);

        assertThat(latest.id()).isEqualTo(50L);
        assertThat(latest.stopLossPrice()).isEqualByComparingTo("85.00");
        assertThat(latest.takeProfitPrice()).isEqualByComparingTo("125.00");
        assertThat(latest.autoExitEnabled()).isFalse();
        assertThat(latest.stopLossExitPercent()).isEqualTo(100);
        assertThat(latest.takeProfitExitPercent()).isEqualTo(100);
        assertThat(currentRule.isStopLossConsumed()).isFalse();
        assertThat(currentRule.isTakeProfitConsumed()).isFalse();
    }

    @Test
    void rejectsExitPercentOutsideInclusiveRange() {
        TrainingSessionChart chart = chart();
        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(chart));

        for (int invalid : new int[]{-1, 0, 101}) {
            RiskRuleUpsertRequest request = new RiskRuleUpsertRequest(
                    new BigDecimal("90"), invalid, new BigDecimal("120"), 100, true
            );
            assertThatThrownBy(() -> service.upsert(7L, 1L, request))
                    .isInstanceOf(com.tradenova.common.exception.CustomException.class);
        }
    }

    @Test
    void stopLossCanTriggerAgainOnlyAfterAnExplicitUpsertRearmsTheRule() {
        assertConsumedTriggerCanOnlyRepeatAfterUpsert(AutoExitReason.STOP_LOSS);
    }

    @Test
    void takeProfitCanTriggerAgainOnlyAfterAnExplicitUpsertRearmsTheRule() {
        assertConsumedTriggerCanOnlyRepeatAfterUpsert(AutoExitReason.TAKE_PROFIT);
    }

    private void assertConsumedTriggerCanOnlyRepeatAfterUpsert(AutoExitReason reason) {
        TrainingSessionChart chart = chart();
        TrainingSessionCandle currentCandle = candle();
        TrainingRiskRule rule = TrainingRiskRule.builder()
                .id(50L)
                .chartId(1L)
                .accountId(10L)
                .stopLossPrice(new BigDecimal("95"))
                .stopLossExitPercent(50)
                .takeProfitPrice(new BigDecimal("105"))
                .takeProfitExitPercent(50)
                .enabled(true)
                .build();
        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(chart));
        when(candleRepo.findByChartIdAndIdx(1L, 4)).thenReturn(Optional.of(currentCandle));
        when(riskRepo.findByChartId(1L)).thenReturn(Optional.of(rule));
        when(riskRepo.save(rule)).thenReturn(rule);
        when(historyRepo.save(any(TrainingRiskRuleHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TrainingAutoExitService autoExit = new TrainingAutoExitService(riskRepo);
        TrainingRiskRuleLifecycleService lifecycle =
                new TrainingRiskRuleLifecycleService(riskRepo, historyRepo);
        TrainingSessionCandle triggerCandle = reason == AutoExitReason.STOP_LOSS
                ? TrainingSessionCandle.builder()
                .o(100.0)
                .h(101.0)
                .l(90.0)
                .c(95.0)
                .build()
                : TrainingSessionCandle.builder()
                .o(100.0)
                .h(110.0)
                .l(99.0)
                .c(105.0)
                .build();

        TrainingAutoExitService.AutoExitResult first =
                autoExit.checkAndAutoExit(1L, triggerCandle);
        lifecycle.consumeTrigger(1L, first.reason());
        TrainingAutoExitService.AutoExitResult withoutSave =
                autoExit.checkAndAutoExit(1L, triggerCandle);

        assertThat(first.reason()).isEqualTo(reason);
        assertThat(withoutSave.autoExited()).isFalse();
        assertThat(reason == AutoExitReason.STOP_LOSS
                ? rule.isStopLossConsumed() : rule.isTakeProfitConsumed()).isTrue();

        service.upsert(7L, 1L, request("94.00", "106.00", true));
        TrainingAutoExitService.AutoExitResult afterSave =
                autoExit.checkAndAutoExit(1L, triggerCandle);

        assertThat(rule.isStopLossConsumed()).isFalse();
        assertThat(rule.isTakeProfitConsumed()).isFalse();
        assertThat(afterSave.reason()).isEqualTo(reason);
        assertThat(afterSave.exitPercent()).isEqualTo(100);
    }

    private TrainingRiskRule currentRule;

    private static void assertHistory(
            TrainingRiskRuleHistory history,
            String stopLoss,
            String takeProfit,
            boolean enabled
    ) {
        assertThat(history.getRiskRuleId()).isEqualTo(50L);
        assertThat(history.getUserId()).isEqualTo(7L);
        assertThat(history.getSessionId()).isEqualTo(40L);
        assertThat(history.getChartId()).isEqualTo(1L);
        assertThat(history.getAccountId()).isEqualTo(10L);
        assertThat(history.getStopLossPrice()).isEqualByComparingTo(stopLoss);
        assertThat(history.getStopLossExitPercent()).isEqualTo(100);
        assertThat(history.getTakeProfitPrice()).isEqualByComparingTo(takeProfit);
        assertThat(history.getTakeProfitExitPercent()).isEqualTo(100);
        assertThat(history.isAutoExitEnabled()).isEqualTo(enabled);
        assertThat(history.getProgressIndex()).isEqualTo(4);
        assertThat(history.getCandleTime()).isEqualTo(500L);
    }

    private static RiskRuleUpsertRequest request(String stopLoss, String takeProfit, boolean enabled) {
        return new RiskRuleUpsertRequest(
                new BigDecimal(stopLoss),
                null,
                new BigDecimal(takeProfit),
                null,
                enabled
        );
    }

    private static TrainingSessionChart chart() {
        User user = User.builder().id(7L).build();
        PaperAccount account = PaperAccount.builder().id(10L).user(user).build();
        TrainingSession session = TrainingSession.builder()
                .id(40L)
                .user(user)
                .account(account)
                .status(TrainingStatus.IN_PROGRESS)
                .build();
        return TrainingSessionChart.builder()
                .id(1L)
                .session(session)
                .symbol(Symbol.builder().id(20L).build())
                .chartIndex(0)
                .bars(10)
                .progressIndex(4)
                .status(TrainingChartStatus.IN_PROGRESS)
                .build();
    }

    private static TrainingSessionCandle candle() {
        return TrainingSessionCandle.builder()
                .chartId(1L)
                .idx(4)
                .t(500L)
                .o(100.0)
                .h(105.0)
                .l(95.0)
                .c(100.0)
                .v(1.0)
                .build();
    }
}
