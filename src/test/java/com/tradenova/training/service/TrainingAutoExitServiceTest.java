package com.tradenova.training.service;

import com.tradenova.training.entity.TrainingRiskRule;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.dto.AutoExitReason;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
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
