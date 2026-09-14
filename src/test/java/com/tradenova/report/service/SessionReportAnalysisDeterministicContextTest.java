package com.tradenova.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.SessionAiAnalysisResponse;
import com.tradenova.report.dto.SessionAiAnalysisRequest;
import com.tradenova.report.dto.SessionAiDeterministicContext;
import com.tradenova.report.dto.SessionQualitativeEvidenceContext;
import com.tradenova.report.dto.TrainingEventResponse;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.symbol.dto.SymbolSector;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.analytics.SessionTradeStatistics;
import com.tradenova.training.entity.TrainingChartStatus;
import com.tradenova.training.entity.TrainingMode;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class SessionReportAnalysisDeterministicContextTest {

    @Test
    void passesDeterministicContextWhilePreservingSnapshotsEventsAndResponseContract() {
        TrainingSessionRepository sessionRepository = mock(TrainingSessionRepository.class);
        TrainingSessionChartRepository chartRepository = mock(TrainingSessionChartRepository.class);
        TrainingTradeRepository tradeRepository = mock(TrainingTradeRepository.class);
        TrainingEventRepository eventRepository = mock(TrainingEventRepository.class);
        ReportDocumentRepository documentRepository = mock(ReportDocumentRepository.class);
        TrainingSessionCandleRepository candleRepository = mock(TrainingSessionCandleRepository.class);
        AiAnalysisService aiAnalysisService = mock(AiAnalysisService.class);
        TrainingEventService eventService = mock(TrainingEventService.class);
        SessionAiDeterministicContextService contextService = mock(SessionAiDeterministicContextService.class);
        SessionQualitativeEvidenceResolver evidenceResolver = mock(SessionQualitativeEvidenceResolver.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SessionReportAnalysisService service = new SessionReportAnalysisService(
                sessionRepository,
                chartRepository,
                tradeRepository,
                eventRepository,
                documentRepository,
                candleRepository,
                aiAnalysisService,
                eventService,
                objectMapper,
                contextService,
                evidenceResolver
        );
        TrainingSession session = session();
        TrainingSessionChart chart = chart(session);
        ObjectNode snapshotContent = objectMapper.createObjectNode()
                .put("thesis", "support held")
                .put("entryReason", "pullback")
                .put("exitPlan", "planned exit")
                .put("riskNote", "risk note")
                .put("freeNote", "memo");
        ReportDocument snapshot = ReportDocument.builder()
                .id(30L)
                .userId(1L)
                .chartId(10L)
                .kind(ReportKind.SNAPSHOT)
                .version(1)
                .contentJson(snapshotContent)
                .build();
        TrainingEvent note = TrainingEvent.builder()
                .id(40L)
                .userId(1L)
                .chartId(10L)
                .type(Type.NOTE)
                .summary("note")
                .build();
        SessionAiDeterministicContext deterministicContext = deterministicContext();
        SessionQualitativeEvidenceContext qualitativeContext =
                new SessionQualitativeEvidenceContext(5L, List.of());
        TrainingEventResponse expected = new TrainingEventResponse(
                99L, 10L, "AI", "세션 AI 리뷰", objectMapper.createObjectNode(), Instant.EPOCH
        );

        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));
        when(chartRepository.findAllBySession_IdOrderByChartIndexAsc(5L)).thenReturn(List.of(chart));
        when(eventRepository.findAllByUserIdAndChartIdInAndTypeOrderByIdDesc(
                1L, List.of(10L), Type.AI)).thenReturn(List.of());
        when(tradeRepository.findAllByChartIdInOrderByCreatedAtAsc(List.of(10L))).thenReturn(List.of());
        when(eventRepository.findAllByUserIdAndChartIdInOrderByIdAsc(1L, List.of(10L)))
                .thenReturn(List.of(note));
        when(documentRepository.findAllByUserIdAndChartIdInAndKindOrderByCreatedAtDesc(
                1L, List.of(10L), ReportKind.SNAPSHOT)).thenReturn(List.of(snapshot));
        when(contextService.build(1L, 5L)).thenReturn(deterministicContext);
        when(evidenceResolver.resolve(deterministicContext, List.of(snapshot), List.of(note), List.of()))
                .thenReturn(qualitativeContext);
        SessionAiAnalysisResponse response = new SessionAiAnalysisResponse(
                80, "summary", List.of("warning"), List.of("strength"),
                new SessionAiAnalysisResponse.DecisionReview(
                        "consistent", List.of("Episode 1 used one entry"), "record an entry reason"),
                new SessionAiAnalysisResponse.RiskReview(
                        "changed", List.of("entry and exit plans differ"), "review plan changes"),
                List.of(new SessionAiAnalysisResponse.BehaviorPattern(
                        "multiple entries", List.of("Episode 2 had two entries"),
                        "consistency needs review", "define a repeat-entry rule")),
                List.of("link reasons to episodes")
        );
        when(aiAnalysisService.analyzeSession(any(SessionAiAnalysisRequest.class))).thenReturn(response);
        when(eventService.append(eq(1L), eq(10L), eq(Type.AI), eq("세션 AI 리뷰"), any(ObjectNode.class)))
                .thenReturn(expected);

        TrainingEventResponse actual = service.analyzeSession(1L, 5L);

        assertSame(expected, actual);
        verify(contextService).build(1L, 5L);
        ArgumentCaptor<SessionAiAnalysisRequest> requestCaptor =
                ArgumentCaptor.forClass(SessionAiAnalysisRequest.class);
        verify(aiAnalysisService).analyzeSession(requestCaptor.capture());
        SessionAiAnalysisRequest request = requestCaptor.getValue();
        assertSame(deterministicContext, request.deterministicContext());
        assertSame(qualitativeContext, request.qualitativeEvidenceContext());
        assertEquals(1, request.totalEventCount());
        assertEquals(1, request.snapshots().size());
        assertEquals("support held", request.snapshots().get(0).thesis());
        assertEquals(1, request.charts().size());

        ArgumentCaptor<ObjectNode> payloadCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(eventService).append(eq(1L), eq(10L), eq(Type.AI), eq("세션 AI 리뷰"), payloadCaptor.capture());
        ObjectNode payload = payloadCaptor.getValue();
        assertEquals(2, payload.path("analysisVersion").asInt());
        assertEquals(80, payload.path("score").asInt());
        assertEquals("warning", payload.path("warnings").get(0).asText());
        assertEquals("consistent", payload.path("decisionReview").path("assessment").asText());
        assertEquals("changed", payload.path("riskReview").path("assessment").asText());
        assertEquals("multiple entries", payload.path("behaviorPatterns").get(0).path("pattern").asText());
        assertEquals("link reasons to episodes", payload.path("nextTrainingFocus").get(0).asText());
    }

    @Test
    void inProgressSessionIsRejectedBeforeAnyAnalysisOrChartLookup() {
        TrainingSessionRepository sessionRepository = mock(TrainingSessionRepository.class);
        TrainingSessionChartRepository chartRepository = mock(TrainingSessionChartRepository.class);
        AiAnalysisService aiAnalysisService = mock(AiAnalysisService.class);
        SessionReportAnalysisService service = service(sessionRepository, chartRepository, aiAnalysisService);
        TrainingSession session = session();
        session.setStatus(TrainingStatus.IN_PROGRESS);
        when(sessionRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.analyzeSession(1L, 5L));

        assertEquals(ErrorCode.SESSION_AI_REQUIRES_COMPLETED_SESSION, exception.getErrorCode());
        verify(aiAnalysisService, never()).analyzeSession(any());
        verify(chartRepository, never()).findAllBySession_IdOrderByChartIndexAsc(any());
    }

    @Test
    void ownershipFailureRemainsNotFoundAndDoesNotInvokeAnalysis() {
        TrainingSessionRepository sessionRepository = mock(TrainingSessionRepository.class);
        AiAnalysisService aiAnalysisService = mock(AiAnalysisService.class);
        SessionReportAnalysisService service = service(
                sessionRepository, mock(TrainingSessionChartRepository.class), aiAnalysisService);
        when(sessionRepository.findByIdAndUserId(5L, 99L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> service.analyzeSession(99L, 5L));

        assertEquals(ErrorCode.TRAINING_SESSION_NOT_FOUND, exception.getErrorCode());
        verify(aiAnalysisService, never()).analyzeSession(any());
    }

    private SessionReportAnalysisService service(TrainingSessionRepository sessions,
                                                  TrainingSessionChartRepository charts,
                                                  AiAnalysisService ai) {
        return new SessionReportAnalysisService(
                sessions, charts, mock(TrainingTradeRepository.class), mock(TrainingEventRepository.class),
                mock(ReportDocumentRepository.class), mock(TrainingSessionCandleRepository.class), ai,
                mock(TrainingEventService.class), new ObjectMapper(),
                mock(SessionAiDeterministicContextService.class), mock(SessionQualitativeEvidenceResolver.class)
        );
    }

    private TrainingSession session() {
        return TrainingSession.builder()
                .id(5L)
                .account(PaperAccount.builder().id(2L).build())
                .mode(TrainingMode.RANDOM)
                .status(TrainingStatus.COMPLETED)
                .build();
    }

    private TrainingSessionChart chart(TrainingSession session) {
        return TrainingSessionChart.builder()
                .id(10L)
                .session(session)
                .chartIndex(0)
                .symbol(Symbol.builder()
                        .id(20L)
                        .ticker("TEST")
                        .name("Test Symbol")
                        .trainingSector(SymbolSector.ETC)
                        .build())
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 6, 1))
                .bars(120)
                .progressIndex(119)
                .status(TrainingChartStatus.COMPLETED)
                .active(true)
                .build();
    }

    private SessionAiDeterministicContext deterministicContext() {
        SessionTradeStatistics statistics = new SessionTradeStatistics(
                0, 0, 0, 0, 0, 0, 0,
                null, null, null, null, null,
                java.math.BigDecimal.ZERO,
                null, null, null, null,
                List.of(), List.of(), List.of(), List.of()
        );
        return new SessionAiDeterministicContext(
                5L, 1L, 2L, "RANDOM", "COMPLETED",
                1, 1, 1, 0, 0, statistics, List.of()
        );
    }
}
