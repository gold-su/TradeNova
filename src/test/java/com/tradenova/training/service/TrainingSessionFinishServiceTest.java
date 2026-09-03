package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.market.service.MarketDataService;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.report.service.TrainingEventService;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.*;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingSessionFinishServiceTest {

    @Mock TrainingSessionRepository sessionRepo;
    @Mock TrainingSessionChartRepository chartRepo;
    @Mock SymbolRepository symbolRepo;
    @Mock UserRepository userRepo;
    @Mock MarketDataService marketDataService;
    @Mock PaperAccountRepository accountRepo;
    @Mock TrainingSessionCandleRepository candleRepo;
    @Mock TrainingEventService eventService;
    @Mock TrainingTradeRepository tradeRepo;
    @Mock ReportDocumentRepository documentRepo;
    @Mock TrainingEventRepository eventRepo;
    @Mock TrainingTradeService tradeService;

    TrainingSessionService service;

    @BeforeEach
    void setUp() {
        service = new TrainingSessionService(new ObjectMapper(), sessionRepo, chartRepo, symbolRepo,
                userRepo, marketDataService, accountRepo, candleRepo, eventService, tradeRepo,
                documentRepo, eventRepo, tradeService);
    }

    @Test
    void locksSessionThenAllChartsByIdThenSharedAccountAndLiquidatesBeforeCompletion() {
        Fixture f = fixture();
        when(sessionRepo.findForUpdateByIdAndUserId(100L, 7L)).thenReturn(Optional.of(f.session));
        when(chartRepo.findAllForUpdateBySessionIdOrderByIdAsc(100L)).thenReturn(f.charts);
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(f.account));
        doAnswer(invocation -> {
            assertThat(f.session.getStatus()).isEqualTo(TrainingStatus.IN_PROGRESS);
            assertThat(f.charts).allMatch(c -> c.getStatus() == TrainingChartStatus.IN_PROGRESS);
            return null;
        }).when(tradeService).liquidateForSessionFinish(anyLong(), any(), same(f.account));

        service.finishSession(7L, 100L);

        InOrder order = inOrder(sessionRepo, chartRepo, accountRepo, tradeService);
        order.verify(sessionRepo).findForUpdateByIdAndUserId(100L, 7L);
        order.verify(chartRepo).findAllForUpdateBySessionIdOrderByIdAsc(100L);
        order.verify(accountRepo).findForUpdateById(10L);
        order.verify(tradeService).liquidateForSessionFinish(7L, f.charts.get(0), f.account);
        order.verify(tradeService).liquidateForSessionFinish(7L, f.charts.get(1), f.account);
        assertThat(f.session.getStatus()).isEqualTo(TrainingStatus.COMPLETED);
        assertThat(f.charts).allMatch(c -> c.getStatus() == TrainingChartStatus.COMPLETED);
    }

    @Test
    void liquidationFailureLeavesAllStatesUnchanged() {
        Fixture f = fixture();
        when(sessionRepo.findForUpdateByIdAndUserId(100L, 7L)).thenReturn(Optional.of(f.session));
        when(chartRepo.findAllForUpdateBySessionIdOrderByIdAsc(100L)).thenReturn(f.charts);
        when(accountRepo.findForUpdateById(10L)).thenReturn(Optional.of(f.account));
        when(tradeService.liquidateForSessionFinish(7L, f.charts.get(0), f.account))
                .thenReturn(mock(TradeResponse.class));
        doThrow(new IllegalStateException("sell failed")).when(tradeService)
                .liquidateForSessionFinish(7L, f.charts.get(1), f.account);

        assertThatThrownBy(() -> service.finishSession(7L, 100L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(f.session.getStatus()).isEqualTo(TrainingStatus.IN_PROGRESS);
        assertThat(f.charts).allMatch(c -> c.getStatus() == TrainingChartStatus.IN_PROGRESS);
        verify(eventService, never()).append(anyLong(), anyLong(), any(), anyString(), any());
    }

    @Test
    void rejectsAlreadyCompletedSessionBeforeTakingOtherLocks() {
        Fixture f = fixture();
        f.session.setStatus(TrainingStatus.COMPLETED);
        when(sessionRepo.findForUpdateByIdAndUserId(100L, 7L)).thenReturn(Optional.of(f.session));

        assertThatThrownBy(() -> service.finishSession(7L, 100L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRAINING_SESSION_ALREADY_COMPLETED);
        verifyNoInteractions(chartRepo, tradeService);
        verify(accountRepo, never()).findForUpdateById(anyLong());
    }

    private Fixture fixture() {
        PaperAccount account = PaperAccount.builder().id(10L).build();
        TrainingSession session = TrainingSession.builder().id(100L).user(User.builder().id(7L).build())
                .account(account).status(TrainingStatus.IN_PROGRESS).build();
        Symbol a = Symbol.builder().id(20L).ticker("A").name("A").build();
        Symbol b = Symbol.builder().id(21L).ticker("B").name("B").build();
        TrainingSessionChart first = TrainingSessionChart.builder().id(1L).session(session).symbol(a)
                .chartIndex(1).status(TrainingChartStatus.IN_PROGRESS).active(true).build();
        TrainingSessionChart second = TrainingSessionChart.builder().id(2L).session(session).symbol(b)
                .chartIndex(0).status(TrainingChartStatus.IN_PROGRESS).active(true).build();
        return new Fixture(account, session, List.of(first, second));
    }

    private record Fixture(PaperAccount account, TrainingSession session, List<TrainingSessionChart> charts) {}
}
