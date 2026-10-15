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
                + ", wins=" + statistics.winningClosedEpisodeCount()
                + ", losses=" + statistics.losingClosedEpisodeCount()
                + ", breakeven=" + statistics.breakevenClosedEpisodeCount()
                + ", holdingBarsSampleCount=" + statistics.holdingBarsSampleCount()
                + "\nwinRate=" + value(statistics.winRate())
                + ", averageWinPnl=" + value(statistics.averageWinningRealizedPnl())
                + ", averageLossPnl=" + value(statistics.averageLosingRealizedPnl())
                + ", payoffRatio=" + value(statistics.payoffRatio())
                + ", expectancy=" + value(statistics.expectancy())
                + ", totalRealizedPnl=" + value(statistics.totalRealizedPnl())
                + "\naverageReturnPct=" + value(statistics.averageReturnPct())
                + ", averageHoldingBars=" + value(statistics.averageHoldingBars())
                + ", averageEntryCount=" + value(statistics.averageEntryCount())
                + ", averageExitCount=" + value(statistics.averageExitCount());
    }

    public String formatChartEvidence(SessionAiDeterministicContext context) {
        if (context == null || context.charts().isEmpty()) {
            return "chart evidence unavailable";
        }

        StringBuilder builder = new StringBuilder();
        for (ChartAiDeterministicContext chart : context.charts()) {
            // A no-trade chart without explicit authored evidence has no decision process to evaluate.
            // Qualitative evidence is formatted separately, so suppress descriptive inventory here.
            if (!chart.traded() && chart.episodes().isEmpty()) continue;
            builder.append("- CHART GROUP reference=").append(chart.chartId())
                    .append(", index=").append(chart.chartIndex())
                    .append(", symbol=").append(chart.symbolTicker())
                    .append(", sector=").append(chart.trainingSector())
                    .append(", status=").append(chart.status())
                    .append(", active=").append(chart.active())
                    .append(", refreshed=").append(chart.refreshed())
                    .append(", progress=").append(chart.progressIndex()).append('/').append(chart.bars())
                    .append(", tradeCount=").append(chart.tradeCount())
                    .append(", episodeCount=").append(chart.episodeCount())
                    .append('\n');

            for (TradeEpisodeAiContext episode : chart.episodes()) {
                builder.append("  * EPISODE reference=").append(chart.chartId()).append(':').append(episode.episodeIndex())
                        .append(", state=").append(episode.closed() ? "CLOSED" : "OPEN")
                        .append(", entryCount=").append(episode.entryCount())
                        .append(", exitCount=").append(episode.exitCount())
                        .append(", weightedEntryPrice=").append(value(episode.weightedEntryPrice()))
                        .append(", weightedExitPrice=").append(value(episode.weightedExitPrice()))
                        .append(", realizedPnl=").append(value(episode.realizedPnl()))
                        .append(", returnPct=").append(value(episode.returnPct()))
                        .append(", holdingBars=").append(value(episode.holdingBars()))
                        .append(", remainingQty=").append(value(episode.remainingQty()))
                        .append(", plan captured at entry=").append(formatRiskPlan(episode.entryRiskPlan()))
                        .append(", plan captured at exit=").append(formatRiskPlan(episode.exitRiskPlan()))
                        .append('\n');
            }
        }
        return builder.toString();
    }

    private String formatRiskPlan(RiskPlanAiContext plan) {
        if (plan == null) {
            return "none";
        }
        return "{configured stop=" + value(plan.stopLossPrice())
                + ", configured target=" + value(plan.takeProfitPrice())
                + ", automatic exit=" + plan.autoExitEnabled()
                + ", captured progress=" + value(plan.progressIndex())
                + ", captured candle=" + value(plan.candleTime())
                + "}";
    }

    private String value(Object value) {
        return value == null ? UNDEFINED : value.toString();
    }
}
