package com.tradenova.training.analytics;

import com.tradenova.training.entity.TradeSide;
import com.tradenova.training.entity.TrainingTrade;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeEpisodeAnalysisServiceTest {

    @Test
    void loadsAccountSymbolOpeningHistoryForCarriedPositionSell() {
        TrainingTradeRepository tradeRepository = mock(TrainingTradeRepository.class);
        TrainingSessionCandleRepository candleRepository = mock(TrainingSessionCandleRepository.class);
        TradeEpisodeAnalysisService service = new TradeEpisodeAnalysisService(
                tradeRepository,
                candleRepository,
                new TradeEpisodeCalculator()
        );
        TrainingTrade priorBuy = trade(144, 491, TradeSide.BUY, 1, 24788);
        TrainingTrade terminalSell = trade(281, 635, TradeSide.SELL, 1, 25390);

        when(tradeRepository.findAllByChartIdOrderByIdAsc(635L))
                .thenReturn(List.of(terminalSell));
        when(tradeRepository.findAllByAccountIdAndSymbolIdAndIdLessThanOrderByIdAsc(
                2L, 3L, 281L
        )).thenReturn(List.of(priorBuy));
        when(candleRepository.findAllByChartIdOrderByIdxAsc(635L)).thenReturn(List.of());

        TradeEpisodeAnalysisResult result = service.analyzeChart(635L);

        assertEquals(635L, result.chartId());
        assertEquals(1, result.episodes().size());
        assertEquals(List.of(281L), result.episodes().get(0).allTradeIds());
        assertTrue(result.episodes().get(0).closed());
        verify(tradeRepository)
                .findAllByAccountIdAndSymbolIdAndIdLessThanOrderByIdAsc(2L, 3L, 281L);
    }

    private TrainingTrade trade(
            long id,
            long chartId,
            TradeSide side,
            int qty,
            int price
    ) {
        return TrainingTrade.builder()
                .id(id)
                .chartId(chartId)
                .accountId(2L)
                .symbolId(3L)
                .side(side)
                .qty(BigDecimal.valueOf(qty))
                .price(BigDecimal.valueOf(price))
                .candleTime((long) id)
                .build();
    }
}
