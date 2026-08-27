package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.common.exception.CustomException;
import com.tradenova.market.service.MarketDataService;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.report.service.TrainingEventService;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import com.tradenova.training.dto.SessionFinishResponse;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.TrainingChartStatus;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import com.tradenova.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingSessionFinishServiceTest {

    @Mock private TrainingSessionRepository sessionRepository;
    @Mock private TrainingSessionChartRepository chartRepository;
    @Mock private SymbolRepository symbolRepository;
    @Mock private UserRepository userRepository;
    @Mock private MarketDataService marketDataService;
    @Mock private PaperAccountRepository accountRepository;
    @Mock private TrainingSessionCandleRepository candleRepository;
    @Mock private TrainingEventService eventService;
    @Mock private TrainingTradeRepository tradeRepository;
    @Mock private ReportDocumentRepository reportDocumentRepository;
    @Mock private TrainingEventRepository eventRepository;
    @Mock private TrainingTradeService tradeService;

    private TrainingSessionService service;

    @BeforeEach
    void setUp() {
        service = new TrainingSessionService(
                new ObjectMapper(), sessionRepository, chartRepository, symbolRepository,
                userRepository, marketDataService, accountRepository, candleRepository,
                eventService, tradeRepository, reportDocumentRepository, eventRepository, tradeService
        );
    }

    @Test
    void closesAllPositionsBeforeCompletingTheSession() {
        Fixture fixture = fixture();
        stubLocks(fixture);
        when(tradeService.sellAllForSessionFinishLocked(7L, fixture.first(), fixture.account()))
                .thenReturn(closeResult(51L, 1L));
        when(tradeService.sellAllForSessionFinishLocked(7L, fixture.second(), fixture.account()))
                .thenReturn(closeResult(null, 2L));

        SessionFinishResponse response = service.finishSession(7L, 40L);

        assertThat(response.sessionStatus()).isEqualTo(TrainingStatus.COMPLETED.name());
        assertThat(fixture.session().getStatus()).isEqualTo(TrainingStatus.COMPLETED);
        assertThat(fixture.first().getStatus()).isEqualTo(TrainingChartStatus.COMPLETED);
        assertThat(fixture.second().getStatus()).isEqualTo(TrainingChartStatus.COMPLETED);

        InOrder order = inOrder(sessionRepository, chartRepository, accountRepository, tradeService);
        order.verify(sessionRepository).findForUpdateByIdAndUserId(40L, 7L);
        order.verify(chartRepository).findAllForUpdateBySessionIdAndUserIdOrderByIdAsc(40L, 7L);
        order.verify(accountRepository).findForUpdateById(10L);
        order.verify(tradeService).sellAllForSessionFinishLocked(7L, fixture.first(), fixture.account());
        order.verify(tradeService).sellAllForSessionFinishLocked(7L, fixture.second(), fixture.account());
    }

    @Test
    void closeFailureLeavesEveryStatusInProgress() {
        Fixture fixture = fixture();
        stubLocks(fixture);
        when(tradeService.sellAllForSessionFinishLocked(7L, fixture.first(), fixture.account()))
                .thenReturn(closeResult(51L, 1L));
        when(tradeService.sellAllForSessionFinishLocked(7L, fixture.second(), fixture.account()))
                .thenThrow(new RuntimeException("close failed"));

        assertThatThrownBy(() -> service.finishSession(7L, 40L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("close failed");

        assertThat(fixture.session().getStatus()).isEqualTo(TrainingStatus.IN_PROGRESS);
        assertThat(fixture.first().getStatus()).isEqualTo(TrainingChartStatus.IN_PROGRESS);
        assertThat(fixture.second().getStatus()).isEqualTo(TrainingChartStatus.IN_PROGRESS);
        verify(eventService, never()).append(any(), any(), any(), any(), any());
    }

    @Test
    void completedSessionStillRejectsAnotherFinish() {
        Fixture fixture = fixture();
        fixture.session().setStatus(TrainingStatus.COMPLETED);
        when(sessionRepository.findForUpdateByIdAndUserId(40L, 7L))
                .thenReturn(Optional.of(fixture.session()));

        assertThatThrownBy(() -> service.finishSession(7L, 40L))
                .isInstanceOf(CustomException.class);

        verify(chartRepository, never())
                .findAllForUpdateBySessionIdAndUserIdOrderByIdAsc(any(), any());
        verify(tradeService, never()).sellAllForSessionFinishLocked(any(), any(), any());
    }

    private void stubLocks(Fixture fixture) {
        when(sessionRepository.findForUpdateByIdAndUserId(40L, 7L))
                .thenReturn(Optional.of(fixture.session()));
        when(chartRepository.findAllForUpdateBySessionIdAndUserIdOrderByIdAsc(40L, 7L))
                .thenReturn(List.of(fixture.first(), fixture.second()));
        when(accountRepository.findForUpdateById(10L)).thenReturn(Optional.of(fixture.account()));
    }

    private static TrainingTradeService.LockedSellResult closeResult(Long tradeId, Long chartId) {
        return new TrainingTradeService.LockedSellResult(
                new TradeResponse(
                        chartId, tradeId, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ONE, 100L
                ),
                tradeId == null ? BigDecimal.ZERO : BigDecimal.ONE
        );
    }

    private static Fixture fixture() {
        PaperAccount account = PaperAccount.builder().id(10L).cashBalance(BigDecimal.TEN).build();
        TrainingSession session = TrainingSession.builder()
                .id(40L).account(account).status(TrainingStatus.IN_PROGRESS).build();
        return new Fixture(
                account,
                session,
                chart(1L, 0, 20L, session),
                chart(2L, 1, 21L, session)
        );
    }

    private static TrainingSessionChart chart(
            Long id, int chartIndex, Long symbolId, TrainingSession session
    ) {
        return TrainingSessionChart.builder()
                .id(id)
                .session(session)
                .symbol(Symbol.builder().id(symbolId).ticker("T" + symbolId).name("S" + symbolId).build())
                .chartIndex(chartIndex)
                .bars(2)
                .progressIndex(0)
                .status(TrainingChartStatus.IN_PROGRESS)
                .active(true)
                .build();
    }

    private record Fixture(
            PaperAccount account,
            TrainingSession session,
            TrainingSessionChart first,
            TrainingSessionChart second
    ) {
    }
}
