package com.tradenova.training.service;

import com.tradenova.training.entity.TrainingRiskRule;
import com.tradenova.training.entity.TrainingRiskRuleHistory;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.dto.AutoExitReason;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Applies live risk-rule state transitions that are caused by a trade lifecycle.
 * Callers must already hold the chart lock and an active transaction.
 */
@Service
@RequiredArgsConstructor
public class TrainingRiskRuleLifecycleService {

    private final TrainingRiskRuleRepository riskRuleRepository;
    private final TrainingRiskRuleHistoryRepository riskRuleHistoryRepository;

    /**
     * Ends the current episode's live risk plan after its position has been fully closed.
     * Missing and already-disabled rules are no-ops; history is appended only for an
     * actual enabled -> disabled transition.
     */
    void disableAfterPositionClosed(
            Long userId,
            TrainingSessionChart chart,
            Long candleTime
    ) {
        TrainingRiskRule rule = riskRuleRepository.findByChartId(chart.getId()).orElse(null);
        if (rule == null || !rule.isEnabled()) {
            return;
        }

        rule.setEnabled(false);
        TrainingRiskRule saved = riskRuleRepository.save(rule);

        riskRuleHistoryRepository.save(
                TrainingRiskRuleHistory.builder()
                        .riskRuleId(saved.getId())
                        .userId(userId)
                        .sessionId(chart.getSession().getId())
                        .chartId(chart.getId())
                        .accountId(chart.getSession().getAccount().getId())
                        .stopLossPrice(saved.getStopLossPrice())
                        .stopLossExitPercent(saved.getStopLossExitPercent())
                        .takeProfitPrice(saved.getTakeProfitPrice())
                        .takeProfitExitPercent(saved.getTakeProfitExitPercent())
                        .autoExitEnabled(false)
                        .progressIndex(chart.getProgressIndex())
                        .candleTime(candleTime)
                        .build()
        );
    }

    void consumeTrigger(Long chartId, AutoExitReason reason) {
        TrainingRiskRule rule = riskRuleRepository.findByChartId(chartId).orElse(null);
        if (rule == null) {
            return;
        }
        if (reason == AutoExitReason.STOP_LOSS) {
            rule.setStopLossConsumed(true);
        } else if (reason == AutoExitReason.TAKE_PROFIT) {
            rule.setTakeProfitConsumed(true);
        } else {
            return;
        }
        riskRuleRepository.save(rule);
    }
}
