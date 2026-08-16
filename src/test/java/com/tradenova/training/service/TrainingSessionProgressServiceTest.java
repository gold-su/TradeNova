package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.entity.PaperPosition;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.report.service.TrainingEventService;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.dto.AutoExitReason;
import com.tradenova.training.dto.SessionProgressResponse;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.TrainingChartStatus;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingSessionProgressServiceTest {

    @Mock private TrainingSessionChartRepository chartRepo;
    @Mock private TrainingAutoExitService autoExitService;
    @Mock private PaperPositionRepository positionRepo;
    @Mock private TrainingTradeService tradeService;
    @Mock private TrainingEventService eventService;
    @Mock private TrainingSessionCandleRepository candleRepo;

    private TrainingSessionProgressService service;

    @BeforeEach
    void setUp() {
        service = new TrainingSessionProgressService(
                chartRepo,
                autoExitService,
                positionRepo,
                tradeService,
                eventService,
                new ObjectMapper(),
                candleRepo
        );
    }

    @Test
    void advanceToLastBarLiquidatesRemainingPositionAtCloseBeforeCompletion() {
        Fixture fixture = fixture(3, 0);
        PaperPosition position = position(fixture.account(), fixture.symbol(), "2");
        TrainingSessionCandle first = candle(fixture.chart().getId(), 0, 100L, 100.0);
        TrainingSessionCandle middle = candle(fixture.chart().getId(), 1, 200L, 105.0);
        TrainingSessionCandle last = candle(fixture.chart().getId(), 2, 300L, 110.0);

        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(first));
        when(candleRepo.findByChartIdAndIdx(1L, 1)).thenReturn(Optional.of(middle));
        when(candleRepo.findByChartIdAndIdx(1L, 2)).thenReturn(Optional.of(last));
        when(autoExitService.checkAndAutoExit(eq(1L), any()))
                .thenReturn(noAutoExit(105.0), noAutoExit(110.0));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L))
                .thenReturn(Optional.of(position), Optional.empty());
        when(tradeService.sellAllAtPriceLockedResult(
                7L, fixture.chart(), BigDecimal.valueOf(110.0), 300L, AutoExitReason.END_OF_CHART
        )).thenAnswer(invocation -> {
            fixture.account().setCashBalance(new BigDecimal("1220.00"));
            return new TrainingTradeService.LockedSellResult(
                    new TradeResponse(
                            1L, 99L, fixture.account().getCashBalance(), BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.valueOf(110.0), 300L
                    ),
                    new BigDecimal("2")
            );
        });

        SessionProgressResponse response = service.advance(7L, 1L, 2);

        assertThat(response.progressIndex()).isEqualTo(2);
        assertThat(response.atLastBar()).isTrue();
        assertThat(response.chartStatus()).isEqualTo(TrainingChartStatus.COMPLETED.name());
        assertThat(response.cashBalance()).isEqualByComparingTo("1220.00");
        assertThat(response.positionQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.autoExited()).isTrue();
        assertThat(response.reason()).isEqualTo(AutoExitReason.END_OF_CHART);
        verify(tradeService).sellAllAtPriceLockedResult(
                7L, fixture.chart(), BigDecimal.valueOf(110.0), 300L, AutoExitReason.END_OF_CHART
        );
    }

    @Test
    void advanceToLastBarWithoutPositionCompletesWithoutTrade() {
        Fixture fixture = fixture(2, 0);
        TrainingSessionCandle first = candle(1L, 0, 100L, 100.0);
        TrainingSessionCandle last = candle(1L, 1, 200L, 110.0);

        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(first));
        when(candleRepo.findByChartIdAndIdx(1L, 1)).thenReturn(Optional.of(last));
        when(autoExitService.checkAndAutoExit(1L, last)).thenReturn(noAutoExit(110.0));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L)).thenReturn(Optional.empty());
        when(tradeService.sellAllAtPriceLockedResult(
                7L, fixture.chart(), BigDecimal.valueOf(110.0), 200L, AutoExitReason.END_OF_CHART
        )).thenReturn(new TrainingTradeService.LockedSellResult(
                new TradeResponse(
                        1L, null, fixture.account().getCashBalance(), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.valueOf(110.0), 200L
                ),
                BigDecimal.ZERO
        ));

        SessionProgressResponse response = service.next(7L, 1L);

        assertThat(response.chartStatus()).isEqualTo(TrainingChartStatus.COMPLETED.name());
        assertThat(response.positionQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.autoExited()).isFalse();
        assertThat(response.reason()).isNull();
        verify(tradeService).sellAllAtPriceLockedResult(
                7L, fixture.chart(), BigDecimal.valueOf(110.0), 200L, AutoExitReason.END_OF_CHART
        );
    }

    @Test
    void stopLossDuringAdvanceStopsAtTriggerAndDoesNotAlsoLiquidateAtEndOfChart() {
        Fixture fixture = fixture(4, 0);
        PaperPosition position = position(fixture.account(), fixture.symbol(), "2");
        TrainingSessionCandle first = candle(1L, 0, 100L, 100.0);
        TrainingSessionCandle trigger = candle(1L, 1, 200L, 90.0);

        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(first));
        when(candleRepo.findByChartIdAndIdx(1L, 1)).thenReturn(Optional.of(trigger));
        when(autoExitService.checkAndAutoExit(1L, trigger)).thenReturn(
                new TrainingAutoExitService.AutoExitResult(
                        true, AutoExitReason.STOP_LOSS, BigDecimal.valueOf(90.0), BigDecimal.valueOf(95.0)
                )
        );
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L))
                .thenReturn(Optional.of(position), Optional.empty());
        when(tradeService.sellAllAtPriceLockedResult(
                7L, fixture.chart(), BigDecimal.valueOf(95.0), 200L, AutoExitReason.STOP_LOSS
        )).thenReturn(new TrainingTradeService.LockedSellResult(
                new TradeResponse(
                        1L, 88L, new BigDecimal("1190.00"), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.valueOf(95.0), 200L
                ),
                new BigDecimal("2")
        ));

        SessionProgressResponse response = service.advance(7L, 1L, 3);

        assertThat(response.progressIndex()).isEqualTo(1);
        assertThat(response.atLastBar()).isFalse();
        assertThat(response.chartStatus()).isEqualTo(TrainingChartStatus.IN_PROGRESS.name());
        assertThat(response.reason()).isEqualTo(AutoExitReason.STOP_LOSS);
        verify(tradeService).sellAllAtPriceLockedResult(
                7L, fixture.chart(), BigDecimal.valueOf(95.0), 200L, AutoExitReason.STOP_LOSS
        );
        verify(candleRepo, never()).findByChartIdAndIdx(1L, 2);
        verify(candleRepo, never()).findByChartIdAndIdx(1L, 3);
    }

    private static TrainingAutoExitService.AutoExitResult noAutoExit(double close) {
        return new TrainingAutoExitService.AutoExitResult(false, null, BigDecimal.valueOf(close), null);
    }

    private static TrainingSessionCandle candle(Long chartId, int idx, long time, double close) {
        return TrainingSessionCandle.builder()
                .chartId(chartId)
                .idx(idx)
                .t(time)
                .o(close)
                .h(close)
                .l(close)
                .c(close)
                .v(1.0)
                .build();
    }

    private static PaperPosition position(PaperAccount account, Symbol symbol, String quantity) {
        return PaperPosition.builder()
                .id(30L)
                .account(account)
                .symbolId(symbol.getId())
                .quantity(new BigDecimal(quantity))
                .avgPrice(new BigDecimal("100.00"))
                .build();
    }

    private static Fixture fixture(int bars, int progressIndex) {
        PaperAccount account = PaperAccount.builder()
                .id(10L)
                .cashBalance(new BigDecimal("1000.00"))
                .build();
        Symbol symbol = Symbol.builder().id(20L).name("테스트 종목").build();
        TrainingSession session = TrainingSession.builder()
                .id(40L)
                .account(account)
                .status(TrainingStatus.IN_PROGRESS)
                .build();
        TrainingSessionChart chart = TrainingSessionChart.builder()
                .id(1L)
                .session(session)
                .symbol(symbol)
                .chartIndex(0)
                .bars(bars)
                .progressIndex(progressIndex)
                .status(TrainingChartStatus.IN_PROGRESS)
                .build();
        return new Fixture(account, symbol, chart);
    }

    private record Fixture(PaperAccount account, Symbol symbol, TrainingSessionChart chart) {
    }
}
