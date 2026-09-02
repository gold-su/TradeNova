package com.tradenova.training.service;

import com.tradenova.training.entity.TrainingRiskRule;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.dto.AutoExitReason;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingAutoExitServiceTest {

    @Test
    void stopLossWinsAndReturnsItsConfiguredPercent() {
        TrainingRiskRuleRepository repository = mock(TrainingRiskRuleRepository.class);
        TrainingRiskRule rule = TrainingRiskRule.builder()
                .chartId(1L).accountId(10L).enabled(true)
                .stopLossPrice(new BigDecimal("95")).stopLossExitPercent(50)
                .takeProfitPrice(new BigDecimal("105")).takeProfitExitPercent(25)
                .build();
        when(repository.findByChartId(1L)).thenReturn(Optional.of(rule));

        TrainingAutoExitService.AutoExitResult result =
                new TrainingAutoExitService(repository).checkAndAutoExit(1L, candle());

        assertThat(result.reason()).isEqualTo(AutoExitReason.STOP_LOSS);
        assertThat(result.exitPercent()).isEqualTo(50);
    }

    @Test
    void consumedTakeProfitDoesNotRepeatButOppositeLegRemainsActive() {
        TrainingRiskRuleRepository repository = mock(TrainingRiskRuleRepository.class);
        TrainingRiskRule rule = TrainingRiskRule.builder()
                .chartId(1L).accountId(10L).enabled(true)
                .stopLossPrice(new BigDecimal("95")).stopLossExitPercent(50)
                .takeProfitPrice(new BigDecimal("105")).takeProfitExitPercent(25)
                .takeProfitConsumed(true).build();
        when(repository.findByChartId(1L)).thenReturn(Optional.of(rule));

        TrainingAutoExitService.AutoExitResult result =
                new TrainingAutoExitService(repository).checkAndAutoExit(1L, candle());

        assertThat(result.reason()).isEqualTo(AutoExitReason.STOP_LOSS);
        assertThat(result.exitPercent()).isEqualTo(50);
    }

    @Test
    void lifecycleConsumptionPreventsRepeatedTakeProfitAndLeavesStopLossAvailable() {
        TrainingRiskRuleRepository repository = mock(TrainingRiskRuleRepository.class);
        TrainingRiskRuleHistoryRepository historyRepository =
                mock(TrainingRiskRuleHistoryRepository.class);
        TrainingRiskRule rule = TrainingRiskRule.builder()
                .chartId(1L).accountId(10L).enabled(true)
                .stopLossPrice(new BigDecimal("95")).stopLossExitPercent(50)
                .takeProfitPrice(new BigDecimal("105")).takeProfitExitPercent(50).build();
        when(repository.findByChartId(1L)).thenReturn(Optional.of(rule));
        TrainingAutoExitService autoExit = new TrainingAutoExitService(repository);
        TrainingRiskRuleLifecycleService lifecycle =
                new TrainingRiskRuleLifecycleService(repository, historyRepository);
        TrainingSessionCandle profitOnly = TrainingSessionCandle.builder()
                .chartId(1L).idx(1).t(200L).o(100.0).h(110.0).l(99.0).c(105.0).v(1.0).build();
        TrainingSessionCandle lossOnly = TrainingSessionCandle.builder()
                .chartId(1L).idx(2).t(300L).o(100.0).h(101.0).l(90.0).c(95.0).v(1.0).build();

        TrainingAutoExitService.AutoExitResult first = autoExit.checkAndAutoExit(1L, profitOnly);
        lifecycle.consumeTrigger(1L, first.reason());
        TrainingAutoExitService.AutoExitResult repeated = autoExit.checkAndAutoExit(1L, profitOnly);
        TrainingAutoExitService.AutoExitResult opposite = autoExit.checkAndAutoExit(1L, lossOnly);

        assertThat(first.reason()).isEqualTo(AutoExitReason.TAKE_PROFIT);
        assertThat(first.exitPercent()).isEqualTo(50);
        assertThat(repeated.autoExited()).isFalse();
        assertThat(opposite.reason()).isEqualTo(AutoExitReason.STOP_LOSS);
        assertThat(opposite.exitPercent()).isEqualTo(50);
    }

    @Test
    void disabledRuleFromClosedEpisodeCannotTriggerOnNextAdvance() {
        TrainingRiskRuleRepository riskRuleRepository = mock(TrainingRiskRuleRepository.class);
        TrainingRiskRule rule = TrainingRiskRule.builder()
                .chartId(1L)
                .accountId(10L)
                .stopLossPrice(new BigDecimal("95"))
                .takeProfitPrice(new BigDecimal("120"))
                .enabled(false)
                .build();
        when(riskRuleRepository.findByChartId(1L)).thenReturn(Optional.of(rule));
        TrainingSessionCandle candle = TrainingSessionCandle.builder()
                .chartId(1L)
                .idx(1)
                .t(200L)
                .o(100.0)
                .h(125.0)
                .l(90.0)
                .c(100.0)
                .v(1.0)
                .build();

        TrainingAutoExitService.AutoExitResult result =
                new TrainingAutoExitService(riskRuleRepository).checkAndAutoExit(1L, candle);

        assertThat(result.autoExited()).isFalse();
        assertThat(result.reason()).isNull();
        assertThat(result.executedPrice()).isNull();
    }

    private static TrainingSessionCandle candle() {
        return TrainingSessionCandle.builder().chartId(1L).idx(1).t(200L)
                .o(100.0).h(110.0).l(90.0).c(100.0).v(1.0).build();
    }
}
