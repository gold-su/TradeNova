package com.tradenova.training.service;

import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.training.entity.TrainingRiskRule;
import com.tradenova.training.entity.TrainingRiskRuleHistory;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingRiskRuleLifecycleServiceTest {

    @Mock private TrainingRiskRuleRepository riskRuleRepository;
    @Mock private TrainingRiskRuleHistoryRepository historyRepository;

    @Test
    void newPositionEpisodeClearsBothConsumedLegsWithoutReEnablingRuleOrAppendingHistory() {
        TrainingRiskRuleLifecycleService service =
                new TrainingRiskRuleLifecycleService(riskRuleRepository, historyRepository);
        TrainingRiskRule rule = TrainingRiskRule.builder()
                .id(60L).chartId(1L).accountId(10L).enabled(false)
                .stopLossConsumed(true).takeProfitConsumed(true).build();
        when(riskRuleRepository.findByChartId(1L)).thenReturn(Optional.of(rule));

        service.resetConsumedForNewPositionEpisode(1L);

        assertThat(rule.isStopLossConsumed()).isFalse();
        assertThat(rule.isTakeProfitConsumed()).isFalse();
        assertThat(rule.isEnabled()).isFalse();
        verify(riskRuleRepository).save(rule);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void forcedExitReasonsNeverConsumeEitherRiskLeg() {
        TrainingRiskRuleLifecycleService service =
                new TrainingRiskRuleLifecycleService(riskRuleRepository, historyRepository);
        TrainingRiskRule rule = TrainingRiskRule.builder()
                .id(60L).chartId(1L).accountId(10L).enabled(true).build();
        when(riskRuleRepository.findByChartId(1L)).thenReturn(Optional.of(rule));

        service.consumeTrigger(1L, com.tradenova.training.dto.AutoExitReason.END_OF_CHART);
        service.consumeTrigger(1L, com.tradenova.training.dto.AutoExitReason.END_OF_SESSION);

        assertThat(rule.isStopLossConsumed()).isFalse();
        assertThat(rule.isTakeProfitConsumed()).isFalse();
        verify(riskRuleRepository, never()).save(any());
    }

    @Test
    void enabledRuleIsDisabledAndPostCloseHistoryIsAppended() {
        TrainingRiskRuleLifecycleService service =
                new TrainingRiskRuleLifecycleService(riskRuleRepository, historyRepository);
        TrainingSessionChart chart = chart();
        TrainingRiskRule rule = TrainingRiskRule.builder()
                .id(60L)
                .chartId(1L)
                .accountId(10L)
                .stopLossPrice(new BigDecimal("95"))
                .takeProfitPrice(new BigDecimal("120"))
                .enabled(true)
                .build();
        when(riskRuleRepository.findByChartId(1L)).thenReturn(Optional.of(rule));
        when(riskRuleRepository.save(rule)).thenReturn(rule);

        service.disableAfterPositionClosed(7L, chart, 200L);

        assertThat(rule.isEnabled()).isFalse();
        ArgumentCaptor<TrainingRiskRuleHistory> historyCaptor =
                ArgumentCaptor.forClass(TrainingRiskRuleHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        TrainingRiskRuleHistory history = historyCaptor.getValue();
        assertThat(history.getRiskRuleId()).isEqualTo(60L);
        assertThat(history.getUserId()).isEqualTo(7L);
        assertThat(history.getSessionId()).isEqualTo(40L);
        assertThat(history.getChartId()).isEqualTo(1L);
        assertThat(history.getAccountId()).isEqualTo(10L);
        assertThat(history.getStopLossPrice()).isEqualByComparingTo("95");
        assertThat(history.getTakeProfitPrice()).isEqualByComparingTo("120");
        assertThat(history.isAutoExitEnabled()).isFalse();
        assertThat(history.getProgressIndex()).isEqualTo(3);
        assertThat(history.getCandleTime()).isEqualTo(200L);
    }

    @Test
    void missingRuleDoesNotCreateOneOrAppendHistory() {
        TrainingRiskRuleLifecycleService service =
                new TrainingRiskRuleLifecycleService(riskRuleRepository, historyRepository);
        when(riskRuleRepository.findByChartId(1L)).thenReturn(Optional.empty());

        service.disableAfterPositionClosed(7L, chart(), 200L);

        verify(riskRuleRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void alreadyDisabledRuleDoesNotAppendDuplicateHistory() {
        TrainingRiskRuleLifecycleService service =
                new TrainingRiskRuleLifecycleService(riskRuleRepository, historyRepository);
        TrainingRiskRule rule = TrainingRiskRule.builder()
                .id(60L).chartId(1L).accountId(10L).enabled(false).build();
        when(riskRuleRepository.findByChartId(1L)).thenReturn(Optional.of(rule));

        service.disableAfterPositionClosed(7L, chart(), 200L);

        verify(riskRuleRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    private static TrainingSessionChart chart() {
        PaperAccount account = PaperAccount.builder().id(10L).build();
        TrainingSession session = TrainingSession.builder().id(40L).account(account).build();
        return TrainingSessionChart.builder()
                .id(1L)
                .session(session)
                .progressIndex(3)
                .build();
    }
}
