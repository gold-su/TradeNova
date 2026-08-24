package com.tradenova.training.analytics;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ownership-checked read boundary for deterministic session statistics.
 */
@Service
@RequiredArgsConstructor
public class SessionTradeStatisticsService {

    private final TrainingSessionRepository sessionRepository;
    private final TrainingSessionChartRepository chartRepository;
    private final TradeEpisodeAnalysisService episodeAnalysisService;
    private final SessionTradeStatisticsCalculator statisticsCalculator;

    @Transactional(readOnly = true)
    public SessionTradeStatistics analyzeSession(Long userId, Long sessionId) {
        TrainingSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // Include inactive refresh history so past trade evidence is never dropped.
        List<TradeEpisodeAnalysisResult> chartResults = chartRepository
                .findAllBySession_IdOrderByChartIndexAsc(session.getId())
                .stream()
                .map(TrainingSessionChart::getId)
                .map(episodeAnalysisService::analyzeChart)
                .toList();

        return statisticsCalculator.calculate(chartResults);
    }
}
