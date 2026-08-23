package com.tradenova.report.service;

import com.tradenova.report.dto.ChartAiDeterministicContext;
import com.tradenova.report.dto.SessionAiDeterministicContext;
import com.tradenova.report.dto.TradeEpisodeAiContext;
import com.tradenova.training.analytics.SessionTradeStatistics;
import org.springframework.stereotype.Component;

import java.util.List;

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
        return "sessionId=" + context.sessionId()
                + ", accountId=" + context.accountId()
                + ", mode=" + context.mode()
                + ", status=" + context.sessionStatus()
                + ", totalChartCount=" + context.totalChartCount()
                + ", activeChartCount=" + context.activeChartCount()
                + ", completedChartCount=" + context.completedChartCount()
                + ", tradedChartCount=" + context.tradedChartCount()
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
            builder.append("- chartId=").append(chart.chartId())
                    .append(", chartIndex=").append(chart.chartIndex())
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
                builder.append("  * episode=").append(chart.chartId()).append(':').append(episode.episodeIndex())
                        .append(", state=").append(episode.closed() ? "CLOSED" : "OPEN")
                        .append(", entryCount=").append(episode.entryCount())
                        .append(", exitCount=").append(episode.exitCount())
                        .append(", weightedEntryPrice=").append(value(episode.weightedEntryPrice()))
                        .append(", weightedExitPrice=").append(value(episode.weightedExitPrice()))
                        .append(", realizedPnl=").append(value(episode.realizedPnl()))
                        .append(", returnPct=").append(value(episode.returnPct()))
                        .append(", holdingBars=").append(value(episode.holdingBars()))
                        .append(", remainingQty=").append(value(episode.remainingQty()))
                        .append(", firstEntryRiskHistoryId=")
                        .append(value(episode.firstEntryRiskRuleHistoryId()))
                        .append(", lastExitRiskHistoryId=")
                        .append(value(episode.lastExitRiskRuleHistoryId()))
                        .append(", tradeRefs={entry:").append(compactIds(episode.entryTradeIds()))
                        .append(",exit:").append(compactIds(episode.exitTradeIds())).append("}")
                        .append('\n');
            }
        }
        return builder.toString();
    }

    private String compactIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        if (ids.size() == 1) {
            return "[" + ids.get(0) + "]";
        }
        return "[" + ids.get(0) + ".." + ids.get(ids.size() - 1) + ";count=" + ids.size() + "]";
    }

    private String value(Object value) {
        return value == null ? UNDEFINED : value.toString();
    }
}
