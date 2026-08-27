package com.tradenova.training.analytics;

import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.entity.TrainingTrade;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only boundary that loads canonical source rows before pure episode calculation.
 */
@Service
@RequiredArgsConstructor
public class TradeEpisodeAnalysisService {

    private final TrainingTradeRepository tradeRepository;
    private final TrainingSessionCandleRepository candleRepository;
    private final TradeEpisodeCalculator calculator;

    @Transactional(readOnly = true)
    public TradeEpisodeAnalysisResult analyzeChart(Long chartId) {
        List<TrainingTrade> trades = tradeRepository.findAllByChartIdOrderByIdAsc(chartId);
        Map<Long, Integer> candleIndexes = candleRepository.findAllByChartIdOrderByIdxAsc(chartId)
                .stream()
                .collect(Collectors.toMap(
                        TrainingSessionCandle::getT,
                        TrainingSessionCandle::getIdx,
                        (first, ignored) -> first
                ));
        return new TradeEpisodeAnalysisResult(chartId, calculator.calculate(trades, candleIndexes));
    }
}
