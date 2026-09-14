package com.tradenova.report.dto;

import com.tradenova.training.analytics.TradeEpisodeReference;
import java.math.BigDecimal;

/** Internal prompt evidence, not a public response or a plan-quality score. */
public record RiskComplianceAiEvidence(
        TradeEpisodeReference episode, Long tradeId, Long riskRuleHistoryId,
        Boolean stopLossConfigured, Boolean takeProfitConfigured, Boolean autoExitEnabledAtTrade,
        String triggeredReason, Integer plannedExitPercent, BigDecimal executedExitPercent,
        BigDecimal plannedExitQty, BigDecimal executedExitQty, Boolean actualExitAutomatic,
        String compliance, String basis
) {}
