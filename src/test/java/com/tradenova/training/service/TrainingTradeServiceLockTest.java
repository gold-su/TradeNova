package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.entity.PaperPosition;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.report.service.TrainingEventService;
import com.tradenova.report.entity.Type;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.dto.AutoExitReason;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.TrainingChartStatus;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.entity.TrainingTrade;
import com.tradenova.training.entity.TrainingRiskRuleHistory;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingTradeServiceLockTest {

    @Mock private TrainingSessionChartRepository chartRepo;
    @Mock private TrainingSessionCandleRepository candleRepo;
    @Mock private TrainingTradeRepository tradeRepo;
    @Mock private TrainingRiskRuleHistoryRepository riskHistoryRepo;
    @Mock private PaperAccountRepository accountRepo;
    @Mock private PaperPositionRepository positionRepo;
    @Mock private TrainingEventService eventService;
    @Mock private TrainingRiskRuleLifecycleService riskRuleLifecycleService;

    private TrainingTradeService service;

    @BeforeEach
    void setUp() {
        service = new TrainingTradeService(
                chartRepo,
                candleRepo,
                tradeRepo,
                riskHistoryRepo,
                accountRepo,
                positionRepo,
                riskRuleLifecycleService,
                eventService,
                new ObjectMapper()
        );
    }

    @Test
    void buyLocksChartThenAccountBeforeLookingUpPositionAndUsesLockedAccount() {
        Fixture fixture = fixture();
        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(fixture.lockedAccount()));
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(fixture.candle()));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L)).thenReturn(Optional.empty());
        when(tradeRepo.save(any(TrainingTrade.class))).thenAnswer(invocation -> {
            TrainingTrade trade = invocation.getArgument(0);
            trade.setId(50L);
            return trade;
        });

        TradeResponse response = service.buy(7L, 1L, BigDecimal.ONE);

        assertThat(response.cashBalance()).isEqualByComparingTo("900.00");
        assertThat(fixture.chartAccount().getCashBalance()).isEqualByComparingTo("1.00");
        InOrder order = inOrder(chartRepo, accountRepo, positionRepo);
        order.verify(chartRepo).findForUpdateByIdAndUserId(1L, 7L);
        order.verify(accountRepo).findForUpdateById(10L);
        order.verify(positionRepo).findByAccountIdAndSymbolId(10L, 20L);

        ArgumentCaptor<TrainingTrade> tradeCaptor = ArgumentCaptor.forClass(TrainingTrade.class);
        verify(tradeRepo).save(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getRiskRuleHistoryId()).isNull();

        ArgumentCaptor<JsonNode> payloadCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(eventService).append(eq(7L), eq(1L), eq(Type.TRADE), anyString(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().path("tradeId").asLong()).isEqualTo(50L);
        assertThat(payloadCaptor.getValue().path("riskRuleHistoryId").isNull()).isTrue();
    }

    @Test
    void newEpisodeBuyReferencesLatestDisabledHistory() {
        Fixture fixture = fixture();
        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(fixture.lockedAccount()));
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(fixture.candle()));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L)).thenReturn(Optional.empty());
        when(riskHistoryRepo.findTopByChartIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(TrainingRiskRuleHistory.builder()
                        .id(71L)
                        .autoExitEnabled(false)
                        .build()));
        when(tradeRepo.save(any(TrainingTrade.class))).thenAnswer(invocation -> {
            TrainingTrade trade = invocation.getArgument(0);
            trade.setId(54L);
            return trade;
        });

        service.buy(7L, 1L, BigDecimal.ONE);

        ArgumentCaptor<TrainingTrade> tradeCaptor = ArgumentCaptor.forClass(TrainingTrade.class);
        verify(tradeRepo).save(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getRiskRuleHistoryId()).isEqualTo(71L);
    }

    @ParameterizedTest
    @EnumSource(AutoExitReason.class)
    void automaticExitWithExistingChartLockUsesLatestRiskHistory(AutoExitReason reason) {
        Fixture fixture = fixture();
        PaperPosition position = PaperPosition.builder()
                .id(30L)
                .account(fixture.lockedAccount())
                .symbolId(20L)
                .quantity(new BigDecimal("2"))
                .avgPrice(new BigDecimal("90.00"))
                .build();
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(fixture.lockedAccount()));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L)).thenReturn(Optional.of(position));
        when(riskHistoryRepo.findTopByChartIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(TrainingRiskRuleHistory.builder().id(70L).build()));
        when(tradeRepo.save(any(TrainingTrade.class))).thenAnswer(invocation -> {
            TrainingTrade trade = invocation.getArgument(0);
            trade.setId(51L);
            return trade;
        });

        TradeResponse response = service.sellAllAtPriceLocked(
                7L,
                fixture.chart(),
                new BigDecimal("110.00"),
                100L,
                reason
        );

        assertThat(response.cashBalance()).isEqualByComparingTo("1220.00");
        assertThat(fixture.chartAccount().getCashBalance()).isEqualByComparingTo("1.00");
        InOrder order = inOrder(accountRepo, positionRepo);
        order.verify(accountRepo).findForUpdateById(10L);
        order.verify(positionRepo).findByAccountIdAndSymbolId(10L, 20L);

        ArgumentCaptor<TrainingTrade> tradeCaptor = ArgumentCaptor.forClass(TrainingTrade.class);
        verify(tradeRepo).save(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getRiskRuleHistoryId()).isEqualTo(70L);

        ArgumentCaptor<JsonNode> payloadCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(eventService).append(eq(7L), eq(1L), eq(Type.TRADE), anyString(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().path("tradeId").asLong()).isEqualTo(51L);
        assertThat(payloadCaptor.getValue().path("riskRuleHistoryId").asLong()).isEqualTo(70L);
        verify(riskRuleLifecycleService).disableAfterPositionClosed(7L, fixture.chart(), 100L);
        InOrder closeOrder = inOrder(tradeRepo, riskRuleLifecycleService, eventService);
        closeOrder.verify(tradeRepo).save(any(TrainingTrade.class));
        closeOrder.verify(riskRuleLifecycleService)
                .disableAfterPositionClosed(7L, fixture.chart(), 100L);
        closeOrder.verify(eventService)
                .append(eq(7L), eq(1L), eq(Type.TRADE), anyString(), any(JsonNode.class));
    }

    @Test
    void sameCandleTradesKeepTheLatestHistoryThatExistedAtEachTransaction() {
        Fixture fixture = fixture();
        AtomicReference<PaperPosition> savedPosition = new AtomicReference<>();
        List<TrainingTrade> savedTrades = new ArrayList<>();

        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(fixture.lockedAccount()));
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(fixture.candle()));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(savedPosition.get()));
        when(positionRepo.save(any(PaperPosition.class))).thenAnswer(invocation -> {
            PaperPosition position = invocation.getArgument(0);
            savedPosition.set(position);
            return position;
        });
        when(riskHistoryRepo.findTopByChartIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(TrainingRiskRuleHistory.builder().id(70L).build()))
                .thenReturn(Optional.of(TrainingRiskRuleHistory.builder().id(71L).build()));
        when(tradeRepo.save(any(TrainingTrade.class))).thenAnswer(invocation -> {
            TrainingTrade trade = invocation.getArgument(0);
            trade.setId(50L + savedTrades.size());
            savedTrades.add(trade);
            return trade;
        });

        service.buy(7L, 1L, BigDecimal.ONE);
        service.sell(7L, 1L, BigDecimal.ONE, false);

        assertThat(savedTrades).hasSize(2);
        assertThat(savedTrades.get(0).getSide().name()).isEqualTo("BUY");
        assertThat(savedTrades.get(0).getCandleTime()).isEqualTo(100L);
        assertThat(savedTrades.get(0).getRiskRuleHistoryId()).isEqualTo(70L);
        assertThat(savedTrades.get(1).getSide().name()).isEqualTo("SELL");
        assertThat(savedTrades.get(1).getCandleTime()).isEqualTo(100L);
        assertThat(savedTrades.get(1).getRiskRuleHistoryId()).isEqualTo(71L);

        ArgumentCaptor<JsonNode> payloadCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(eventService, times(2))
                .append(eq(7L), eq(1L), eq(Type.TRADE), anyString(), payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues().get(0).path("tradeId").asLong()).isEqualTo(50L);
        assertThat(payloadCaptor.getAllValues().get(1).path("tradeId").asLong()).isEqualTo(51L);
        verify(riskRuleLifecycleService).disableAfterPositionClosed(7L, fixture.chart(), 100L);
    }

    @Test
    void sellAllCreatesOneCanonicalTradeEventWhenPositionExists() {
        Fixture fixture = fixture();
        PaperPosition position = PaperPosition.builder()
                .id(30L)
                .account(fixture.lockedAccount())
                .symbolId(20L)
                .quantity(new BigDecimal("2"))
                .avgPrice(new BigDecimal("90.00"))
                .build();
        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(fixture.lockedAccount()));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L)).thenReturn(Optional.of(position));
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(fixture.candle()));
        when(riskHistoryRepo.findTopByChartIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(TrainingRiskRuleHistory.builder().id(70L).build()));
        when(tradeRepo.save(any(TrainingTrade.class))).thenAnswer(invocation -> {
            TrainingTrade trade = invocation.getArgument(0);
            trade.setId(52L);
            return trade;
        });

        TradeResponse response = service.sellAll(7L, 1L);

        assertThat(response.tradeId()).isEqualTo(52L);
        ArgumentCaptor<JsonNode> payloadCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(eventService).append(eq(7L), eq(1L), eq(Type.TRADE), anyString(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().path("tradeId").asLong()).isEqualTo(52L);
        assertThat(payloadCaptor.getValue().path("sellAll").asBoolean()).isTrue();
        assertThat(payloadCaptor.getValue().path("riskRuleHistoryId").asLong()).isEqualTo(70L);
        verify(riskRuleLifecycleService).disableAfterPositionClosed(7L, fixture.chart(), 100L);
    }

    @Test
    void sellAllWithoutPositionCreatesNeitherTradeNorTradeEvent() {
        Fixture fixture = fixture();
        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(fixture.lockedAccount()));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L)).thenReturn(Optional.empty());
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(fixture.candle()));

        TradeResponse response = service.sellAll(7L, 1L);

        assertThat(response.tradeId()).isNull();
        verify(tradeRepo, never()).save(any(TrainingTrade.class));
        verify(eventService, never()).append(
                eq(7L), eq(1L), eq(Type.TRADE), anyString(), any(JsonNode.class)
        );
        verify(riskRuleLifecycleService, never())
                .disableAfterPositionClosed(any(), any(), any());
    }

    @Test
    void partialSellKeepsTheCurrentRiskRuleEnabled() {
        Fixture fixture = fixture();
        PaperPosition position = PaperPosition.builder()
                .id(30L)
                .account(fixture.lockedAccount())
                .symbolId(20L)
                .quantity(new BigDecimal("2"))
                .avgPrice(new BigDecimal("90.00"))
                .build();
        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(fixture.lockedAccount()));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L)).thenReturn(Optional.of(position));
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(fixture.candle()));
        when(tradeRepo.save(any(TrainingTrade.class))).thenAnswer(invocation -> {
            TrainingTrade trade = invocation.getArgument(0);
            trade.setId(53L);
            return trade;
        });

        TradeResponse response = service.sell(7L, 1L, BigDecimal.ONE, false);

        assertThat(response.positionQty()).isEqualByComparingTo(BigDecimal.ONE);
        verify(riskRuleLifecycleService, never())
                .disableAfterPositionClosed(any(), any(), any());
    }

    @Test
    void lifecycleFailurePropagatesBeforeTradeEventIsAppended() {
        Fixture fixture = fixture();
        PaperPosition position = PaperPosition.builder()
                .id(30L)
                .account(fixture.lockedAccount())
                .symbolId(20L)
                .quantity(BigDecimal.ONE)
                .avgPrice(new BigDecimal("90.00"))
                .build();
        when(chartRepo.findForUpdateByIdAndUserId(1L, 7L)).thenReturn(Optional.of(fixture.chart()));
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(fixture.lockedAccount()));
        when(positionRepo.findByAccountIdAndSymbolId(10L, 20L)).thenReturn(Optional.of(position));
        when(candleRepo.findByChartIdAndIdx(1L, 0)).thenReturn(Optional.of(fixture.candle()));
        when(tradeRepo.save(any(TrainingTrade.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RuntimeException failure = new RuntimeException("history insert failed");
        org.mockito.Mockito.doThrow(failure)
                .when(riskRuleLifecycleService)
                .disableAfterPositionClosed(7L, fixture.chart(), 100L);

        assertThatThrownBy(() -> service.sell(7L, 1L, BigDecimal.ONE, false))
                .isSameAs(failure);

        verify(eventService, never()).append(
                eq(7L), eq(1L), eq(Type.TRADE), anyString(), any(JsonNode.class)
        );
    }

    private static Fixture fixture() {
        PaperAccount chartAccount = PaperAccount.builder()
                .id(10L)
                .cashBalance(new BigDecimal("1.00"))
                .build();
        PaperAccount lockedAccount = PaperAccount.builder()
                .id(10L)
                .cashBalance(new BigDecimal("1000.00"))
                .build();
        Symbol symbol = Symbol.builder().id(20L).name("테스트 종목").build();
        TrainingSession session = TrainingSession.builder()
                .id(40L)
                .account(chartAccount)
                .status(TrainingStatus.IN_PROGRESS)
                .build();
        TrainingSessionChart chart = TrainingSessionChart.builder()
                .id(1L)
                .session(session)
                .symbol(symbol)
                .chartIndex(0)
                .bars(2)
                .progressIndex(0)
                .status(TrainingChartStatus.IN_PROGRESS)
                .build();
        TrainingSessionCandle candle = TrainingSessionCandle.builder()
                .chartId(1L)
                .idx(0)
                .t(100L)
                .o(100.0)
                .h(100.0)
                .l(100.0)
                .c(100.0)
                .v(1.0)
                .build();
        return new Fixture(chartAccount, lockedAccount, chart, candle);
    }

    private record Fixture(
            PaperAccount chartAccount,
            PaperAccount lockedAccount,
            TrainingSessionChart chart,
            TrainingSessionCandle candle
    ) {
    }
}
