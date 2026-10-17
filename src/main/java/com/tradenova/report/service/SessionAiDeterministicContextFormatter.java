package com.tradenova.report.service;

import com.tradenova.report.dto.ChartAiDeterministicContext;
import com.tradenova.report.dto.RiskPlanAiContext;
import com.tradenova.report.dto.SessionAiDeterministicContext;
import com.tradenova.report.dto.TradeEpisodeAiContext;
import com.tradenova.training.analytics.SessionTradeStatistics;
import org.springframework.stereotype.Component;

/**
 * Compact, explicit prompt serialization for backend-calculated session evidence.
 */
@Component
public class SessionAiDeterministicContextFormatter {

    private static final String UNDEFINED = "undefined";

    public String formatSessionFacts(SessionAiDeterministicContext context) {
        if (context == null) {
            return "deterministic context unavailable";
        }
        return "mode=" + context.mode()
                + ", status=" + context.sessionStatus()
                + ", totalTradeCount=" + context.totalTradeCount();
    }

    public String formatStatistics(SessionAiDeterministicContext context) {
        if (context == null || context.tradeStatistics() == null) {
            return "trade statistics unavailable";
        }
        SessionTradeStatistics statistics = context.tradeStatistics();
        return "totalEpisodes=" + statistics.totalEpisodeCount()
                + ", closedEpisodes=" + statistics.closedEpisodeCount()
                + ", openEpisodes=" + statistics.openEpisodeCount()
                + ", holdingBarsSampleCount=" + statistics.holdingBarsSampleCount()
                + "\naverageHoldingBars=" + value(statistics.averageHoldingBars())
                + ", averageEntryCount=" + value(statistics.averageEntryCount())
                + ", averageExitCount=" + value(statistics.averageExitCount())
                + "\nOutcome statistics are intentionally excluded from qualitative review input.";
    }

    public String formatChartEvidence(SessionAiDeterministicContext context) {
        if (context == null || context.charts().isEmpty()) {
            return "session behavior facts unavailable";
        }

        StringBuilder builder = new StringBuilder();
        int unitIndex = 0;
        for (ChartAiDeterministicContext chart : context.charts()) {
            // A no-trade chart without explicit authored evidence has no decision process to evaluate.
            // Qualitative evidence is formatted separately, so suppress descriptive inventory here.
            if (!chart.traded() && chart.episodes().isEmpty()) continue;
            unitIndex++;
            builder.append("- ANALYSIS UNIT ").append(unitIndex)
                    .append(": status=").append(chart.status())
                    .append(", active=").append(chart.active())
                    .append(", refreshed=").append(chart.refreshed())
                    .append(", tradeCount=").append(chart.tradeCount())
                    .append(", episodeCount=").append(chart.episodeCount())
                    .append('\n');

            int episodeIndex = 0;
            for (TradeEpisodeAiContext episode : chart.episodes()) {
                episodeIndex++;
                builder.append("  * EPISODE ").append(episodeIndex)
                        .append(": state=").append(episode.closed() ? "CLOSED" : "OPEN")
                        .append(", entryCount=").append(episode.entryCount())
                        .append(", exitCount=").append(episode.exitCount())
                        .append(", remainingQty=").append(value(episode.remainingQty()))
                        .append(", entry-time structured risk=").append(formatRiskPlan(episode.entryRiskPlan()))
                        .append(", exit-time structured risk=").append(formatRiskPlan(episode.exitRiskPlan()))
                        .append('\n');
            }
        }
        builder.append("Outcome amounts are intentionally omitted from these qualitative behavior facts. ")
                .append("Use the statistics block only as neutral outcome context, never as process quality.\n");
        return builder.toString();
    }

    private String formatRiskPlan(RiskPlanAiContext plan) {
        if (plan == null) {
            return "none";
        }
        return "{stop configured=" + (plan.stopLossPrice() != null)
                + ", target configured=" + (plan.takeProfitPrice() != null)
                + ", automatic exit configured=" + plan.autoExitEnabled()
                + "}";
    }

    private String value(Object value) {
        return value == null ? UNDEFINED : value.toString();
    }
}
