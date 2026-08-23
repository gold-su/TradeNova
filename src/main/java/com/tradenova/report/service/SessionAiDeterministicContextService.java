package com.tradenova.report.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.ChartAiDeterministicContext;
import com.tradenova.report.dto.RiskPlanAiContext;
import com.tradenova.report.dto.SessionAiDeterministicContext;
import com.tradenova.report.dto.TradeEpisodeAiContext;
import com.tradenova.training.analytics.SessionTradeStatistics;
import com.tradenova.training.analytics.SessionTradeStatisticsCalculator;
import com.tradenova.training.analytics.TradeEpisode;
import com.tradenova.training.analytics.TradeEpisodeAnalysisResult;
import com.tradenova.training.analytics.TradeEpisodeAnalysisService;
import com.tradenova.training.analytics.TradeEpisodeDataException;
import com.tradenova.training.entity.TrainingChartStatus;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingRiskRuleHistory;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ownership-checked boundary that prepares deterministic facts for session AI.
 * It intentionally does not invoke an LLM or modify the current prompt contract.
 */
@Service
@RequiredArgsConstructor
public class SessionAiDeterministicContextService {

    private final TrainingSessionRepository sessionRepository;
    private final TrainingSessionChartRepository chartRepository;
    private final TradeEpisodeAnalysisService episodeAnalysisService;
    private final SessionTradeStatisticsCalculator statisticsCalculator;
    private final TrainingRiskRuleHistoryRepository riskHistoryRepository;

    @Transactional(readOnly = true)
    public SessionAiDeterministicContext build(Long userId, Long sessionId) {
        TrainingSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // Deliberately include inactive refresh history containing past evidence.
        List<TrainingSessionChart> charts =
                chartRepository.findAllBySession_IdOrderByChartIndexAsc(session.getId());
        if (charts.isEmpty()) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND);
        }

        List<TradeEpisodeAnalysisResult> episodeResults = new ArrayList<>(charts.size());

        for (TrainingSessionChart chart : charts) {
            TradeEpisodeAnalysisResult result = episodeAnalysisService.analyzeChart(chart.getId());
            episodeResults.add(result);
        }

        Map<Long, TrainingRiskRuleHistory> riskHistories = loadRiskHistories(episodeResults);
        List<ChartAiDeterministicContext> chartContexts = new ArrayList<>(charts.size());
        for (int index = 0; index < charts.size(); index++) {
            chartContexts.add(toChartContext(
                    session,
                    userId,
                    charts.get(index),
                    episodeResults.get(index),
                    riskHistories
            ));
        }

        SessionTradeStatistics statistics = statisticsCalculator.calculate(episodeResults);
        int activeChartCount = (int) charts.stream().filter(TrainingSessionChart::isActive).count();
        int completedChartCount = (int) charts.stream()
                .filter(chart -> chart.getStatus() == TrainingChartStatus.COMPLETED)
                .count();
        int tradedChartCount = (int) chartContexts.stream()
                .filter(ChartAiDeterministicContext::traded)
                .count();
        int totalTradeCount = chartContexts.stream()
                .mapToInt(ChartAiDeterministicContext::tradeCount)
                .sum();

        return new SessionAiDeterministicContext(
                session.getId(),
                userId,
                session.getAccount().getId(),
                session.getMode().name(),
                session.getStatus().name(),
                charts.size(),
                activeChartCount,
                completedChartCount,
                tradedChartCount,
                totalTradeCount,
                statistics,
                chartContexts
        );
    }

    private ChartAiDeterministicContext toChartContext(
            TrainingSession session,
            Long userId,
            TrainingSessionChart chart,
            TradeEpisodeAnalysisResult result,
            Map<Long, TrainingRiskRuleHistory> riskHistories
    ) {
        List<TradeEpisodeAiContext> episodes = result.episodes().stream()
                .map(episode -> toEpisodeContext(session, userId, chart, episode, riskHistories))
                .toList();
        int tradeCount = episodes.stream().mapToInt(episode -> episode.allTradeIds().size()).sum();

        return new ChartAiDeterministicContext(
                chart.getId(),
                chart.getChartIndex(),
                chart.getSymbol().getId(),
                chart.getSymbol().getTicker(),
                chart.getSymbol().getName(),
                chart.getSymbol().getTrainingSector().name(),
                chart.getStartDate(),
                chart.getEndDate(),
                chart.getBars(),
                chart.getProgressIndex(),
                chart.getStatus().name(),
                chart.isActive(),
                chart.isRefreshed(),
                tradeCount,
                episodes.size(),
                !episodes.isEmpty(),
                episodes
        );
    }

    private TradeEpisodeAiContext toEpisodeContext(
            TrainingSession session,
            Long userId,
            TrainingSessionChart chart,
            TradeEpisode episode,
            Map<Long, TrainingRiskRuleHistory> riskHistories
    ) {
        return new TradeEpisodeAiContext(
                episode.episodeIndex(),
                episode.entryTradeIds(),
                episode.exitTradeIds(),
                episode.allTradeIds(),
                episode.openedAtCandleTime(),
                episode.closedAtCandleTime(),
                episode.entryCount(),
                episode.exitCount(),
                episode.totalEntryQty(),
                episode.totalExitQty(),
                episode.weightedEntryPrice(),
                episode.weightedExitPrice(),
                episode.realizedPnl(),
                episode.returnPct(),
                episode.holdingBars(),
                episode.closed(),
                episode.remainingQty(),
                episode.firstEntryRiskRuleHistoryId(),
                episode.lastExitRiskRuleHistoryId(),
                resolveRiskPlan(
                        episode.firstEntryRiskRuleHistoryId(), session, userId, chart, riskHistories
                ),
                resolveRiskPlan(
                        episode.lastExitRiskRuleHistoryId(), session, userId, chart, riskHistories
                )
        );
    }

    private Map<Long, TrainingRiskRuleHistory> loadRiskHistories(
            List<TradeEpisodeAnalysisResult> episodeResults
    ) {
        Set<Long> historyIds = new HashSet<>();
        episodeResults.stream()
                .flatMap(result -> result.episodes().stream())
                .forEach(episode -> {
                    if (episode.firstEntryRiskRuleHistoryId() != null) {
                        historyIds.add(episode.firstEntryRiskRuleHistoryId());
                    }
                    if (episode.lastExitRiskRuleHistoryId() != null) {
                        historyIds.add(episode.lastExitRiskRuleHistoryId());
                    }
                });

        if (historyIds.isEmpty()) {
            return Map.of();
        }
        return riskHistoryRepository.findAllByIdIn(historyIds).stream()
                .collect(Collectors.toMap(
                        TrainingRiskRuleHistory::getId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private RiskPlanAiContext resolveRiskPlan(
            Long historyId,
            TrainingSession session,
            Long userId,
            TrainingSessionChart chart,
            Map<Long, TrainingRiskRuleHistory> riskHistories
    ) {
        if (historyId == null) {
            return null;
        }
        TrainingRiskRuleHistory history = riskHistories.get(historyId);
        if (history == null) {
            throw new TradeEpisodeDataException("Missing risk history id=" + historyId);
        }
        if (!userId.equals(history.getUserId())
                || !session.getId().equals(history.getSessionId())
                || !session.getAccount().getId().equals(history.getAccountId())
                || !chart.getId().equals(history.getChartId())) {
            throw new TradeEpisodeDataException(
                    "Risk history id=" + historyId + " does not belong to its episode context"
            );
        }
        return new RiskPlanAiContext(
                history.getId(),
                history.getStopLossPrice(),
                history.getTakeProfitPrice(),
                history.isAutoExitEnabled(),
                history.getProgressIndex(),
                history.getCandleTime()
        );
    }
}
