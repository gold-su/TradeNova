package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.kis.dto.CandleDto;
import com.tradenova.market.service.MarketDataService;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.report.service.TrainingEventService;
import com.tradenova.symbol.dto.SymbolSector;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import com.tradenova.training.dto.ChartRefreshRequest;
import com.tradenova.training.dto.ChartRefreshType;
import com.tradenova.training.dto.TrainingSessionCreateRequest;
import com.tradenova.training.entity.TrainingMode;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingSessionSymbolUniquenessTest {

    @Mock private TrainingSessionRepository sessionRepo;
    @Mock private TrainingSessionChartRepository chartRepo;
    @Mock private SymbolRepository symbolRepository;
    @Mock private UserRepository userRepository;
    @Mock private MarketDataService marketDataService;
    @Mock private PaperAccountRepository paperAccountRepository;
    @Mock private TrainingSessionCandleRepository candleRepo;
    @Mock private TrainingEventService trainingEventService;
    @Mock private TrainingTradeRepository tradeRepository;
    @Mock private ReportDocumentRepository reportDocumentRepository;
    @Mock private TrainingEventRepository trainingEventRepository;
    @Mock private TrainingTradeService trainingTradeService;

    private TrainingSessionService service;

    @BeforeEach
    void setUp() {
        service = new TrainingSessionService(
                new ObjectMapper(), sessionRepo, chartRepo, symbolRepository, userRepository,
                marketDataService, paperAccountRepository, candleRepo, trainingEventService,
                tradeRepository, reportDocumentRepository, trainingEventRepository, trainingTradeService
        );
        lenient().when(chartRepo.save(any(TrainingSessionChart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(marketDataService.getCandles(
                        any(Symbol.class), any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(candles(30));
    }

    @Test
    void createsFourChartsWithFourDifferentSymbols() {
        User user = User.builder().id(1L).build();
        PaperAccount account = PaperAccount.builder().id(10L).build();
        when(symbolRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(
                symbol(1), symbol(2), symbol(3), symbol(4)
        ));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(paperAccountRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(account));
        when(sessionRepo.save(any(TrainingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createSession(1L, new TrainingSessionCreateRequest(10L, TrainingMode.RANDOM, 30, 4));

        ArgumentCaptor<TrainingSessionChart> captor = ArgumentCaptor.forClass(TrainingSessionChart.class);
        org.mockito.Mockito.verify(chartRepo, org.mockito.Mockito.times(4)).save(captor.capture());
        Set<Long> symbolIds = captor.getAllValues().stream()
                .map(chart -> chart.getSymbol().getId())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(4, symbolIds.size());
    }

    @Test
    void refreshExcludesEverySymbolPreviouslyUsedBySession() {
        TrainingSession session = TrainingSession.builder()
                .id(100L)
                .status(TrainingStatus.IN_PROGRESS)
                .build();
        TrainingSessionChart current = chart(11L, session, 0, symbol(1), true);
        TrainingSessionChart otherActive = chart(12L, session, 1, symbol(2), true);
        TrainingSessionChart oldInactive = chart(13L, session, 0, symbol(3), false);
        Symbol fresh = symbol(4);

        when(chartRepo.findForUpdateByIdAndUserId(11L, 1L)).thenReturn(Optional.of(current));
        when(tradeRepository.existsByChartId(11L)).thenReturn(false);
        when(chartRepo.findAllBySession_IdOrderByChartIndexAsc(100L))
                .thenReturn(List.of(current, oldInactive, otherActive));
        when(symbolRepository.findAllByActiveTrueOrderByIdAsc())
                .thenReturn(List.of(symbol(1), symbol(2), symbol(3), fresh));

        service.refreshChart(1L, 11L, new ChartRefreshRequest(ChartRefreshType.RANDOM, null));

        ArgumentCaptor<TrainingSessionChart> captor = ArgumentCaptor.forClass(TrainingSessionChart.class);
        org.mockito.Mockito.verify(chartRepo).save(captor.capture());
        assertEquals(4L, captor.getValue().getSymbol().getId());
        assertFalse(current.isActive());
    }

    @Test
    void failsClearlyWhenInitialCandidatesCannotCoverChartCount() {
        when(symbolRepository.findAllByActiveTrueOrderByIdAsc())
                .thenReturn(List.of(symbol(1), symbol(1), symbol(2)));

        CustomException error = assertThrows(CustomException.class, () ->
                service.createSession(1L, new TrainingSessionCreateRequest(10L, TrainingMode.RANDOM, 30, 3))
        );

        assertEquals(ErrorCode.TRAINING_SYMBOL_CANDIDATES_EXHAUSTED, error.getErrorCode());
    }

    @Test
    void failsClearlyWhenRefreshHasNoUnusedCandidate() {
        TrainingSession session = TrainingSession.builder()
                .id(100L)
                .status(TrainingStatus.IN_PROGRESS)
                .build();
        TrainingSessionChart current = chart(11L, session, 0, symbol(1), true);
        TrainingSessionChart other = chart(12L, session, 1, symbol(2), true);

        when(chartRepo.findForUpdateByIdAndUserId(11L, 1L)).thenReturn(Optional.of(current));
        when(chartRepo.findAllBySession_IdOrderByChartIndexAsc(100L)).thenReturn(List.of(current, other));
        when(symbolRepository.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(symbol(1), symbol(2)));

        CustomException error = assertThrows(CustomException.class, () ->
                service.refreshChart(1L, 11L, new ChartRefreshRequest(ChartRefreshType.RANDOM, null))
        );

        assertEquals(ErrorCode.TRAINING_SYMBOL_CANDIDATES_EXHAUSTED, error.getErrorCode());
        assertTrue(current.isActive());
    }

    @Test
    void entityDeclaresSessionAndSymbolDatabaseUniqueness() {
        Table table = TrainingSessionChart.class.getAnnotation(Table.class);

        boolean found = List.of(table.uniqueConstraints()).stream()
                .filter(constraint -> constraint.name().equals("uk_session_chart_session_symbol"))
                .map(UniqueConstraint::columnNames)
                .anyMatch(columns -> List.of(columns).equals(List.of("session_id", "symbol_id")));

        assertTrue(found);
    }

    @Test
    void entityDeclaresOneActiveChartPerSessionSlotUniqueness() {
        Table table = TrainingSessionChart.class.getAnnotation(Table.class);

        boolean found = List.of(table.uniqueConstraints()).stream()
                .filter(constraint -> constraint.name().equals("uk_session_chart_active_slot"))
                .map(UniqueConstraint::columnNames)
                .anyMatch(columns -> List.of(columns).equals(List.of("session_id", "active_chart_index")));

        assertTrue(found);
    }

    @Test
    void refreshRejectsTradedChartBeforeReleasingItsSlot() {
        TrainingSession session = TrainingSession.builder()
                .id(100L)
                .status(TrainingStatus.IN_PROGRESS)
                .build();
        TrainingSessionChart current = chart(11L, session, 0, symbol(1), true);

        when(chartRepo.findForUpdateByIdAndUserId(11L, 1L)).thenReturn(Optional.of(current));
        when(tradeRepository.existsByChartId(11L)).thenReturn(true);

        CustomException error = assertThrows(CustomException.class, () ->
                service.refreshChart(1L, 11L, new ChartRefreshRequest(ChartRefreshType.RANDOM, null))
        );

        assertEquals(ErrorCode.CHART_REFRESH_NOT_ALLOWED_HAS_TRADES, error.getErrorCode());
        assertTrue(current.isActive());
        verify(chartRepo, never()).saveAndFlush(any());
        verify(chartRepo, never()).save(any());
    }

    private TrainingSessionChart chart(
            long id,
            TrainingSession session,
            int chartIndex,
            Symbol symbol,
            boolean active
    ) {
        return TrainingSessionChart.builder()
                .id(id)
                .session(session)
                .chartIndex(chartIndex)
                .symbol(symbol)
                .bars(30)
                .active(active)
                .build();
    }

    private Symbol symbol(long id) {
        return Symbol.builder()
                .id(id)
                .ticker("T" + id)
                .name("Symbol " + id)
                .trainingSector(SymbolSector.ETC)
                .active(true)
                .build();
    }

    private List<CandleDto> candles(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new CandleDto(index + 1L, 100, 110, 90, 100, 1_000))
                .toList();
    }
}
