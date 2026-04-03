package com.tradenova.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.*;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 세션 단위 AI 분석 서비스
 *
 * 역할:
 * - 세션에 포함된 모든 차트/거래/이벤트/스냅샷 데이터를 수집
 * - 차트별 요약(SessionChartSummary) 생성
 * - finalPnL 계산 (실현 + 미실현 포함)
 * - AI에게 분석 요청
 * - 결과를 TrainingEvent 형태로 저장
 *
 * 핵심 특징:
 * - "단일 차트"가 아닌 "멀티 차트 세션 전체" 평가
 * - 행동 데이터 + 결과(finalPnL) 함께 전달
 * - reasoning(snapshot)까지 포함한 종합 평가 구조
 */
@Service
@RequiredArgsConstructor
public class SessionReportAnalysisService {

    private final TrainingSessionRepository sessionRepository;
    private final TrainingSessionChartRepository chartRepository;
    private final TrainingTradeRepository tradeRepository;
    private final TrainingEventRepository eventRepository;
    private final ReportDocumentRepository reportDocumentRepository;
    private final TrainingSessionCandleRepository candleRepository;

    private final AiAnalysisService aiAnalysisService;
    private final TrainingEventService trainingEventService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TrainingEventResponse analyzeSession(Long userId, Long sessionId) {

        // 1) 세션 조회 + 소유권 체크
        TrainingSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // 2) 세션 차트 조회
        List<TrainingSessionChart> charts = chartRepository.findAllBySession_IdOrderByChartIndexAsc(session.getId());
        if (charts.isEmpty()) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND);
        }

        // 중복 방지
        TrainingEventResponse existing = getLatestSessionAi(userId, sessionId);
        if (existing != null) {
            throw new CustomException(ErrorCode.SESSION_AI_ALREADY_EXISTS);
        }

        // chartId 리스트 추출 (이후 모든 조회에서 사용)
        List<Long> chartIds = charts.stream()
                .map(TrainingSessionChart::getId)
                .toList();

        // 3) 세션 전체 거래 / 이벤트 / 스냅샷 조회
        List<TrainingTrade> trades = tradeRepository.findAllByChartIdInOrderByCreatedAtAsc(chartIds);
        List<TrainingEvent> events = eventRepository.findAllByUserIdAndChartIdInOrderByIdAsc(userId, chartIds);
        List<ReportDocument> snapshots = reportDocumentRepository
                .findAllByUserIdAndChartIdInAndKindOrderByCreatedAtDesc(userId, chartIds, ReportKind.SNAPSHOT);

        // 4) chart별 통계 계산 (AI 입력용)
        Map<Long, Long> tradeCountMap = trades.stream()
                .collect(Collectors.groupingBy(TrainingTrade::getChartId, Collectors.counting()));

        Map<Long, Long> eventCountMap = events.stream()
                .collect(Collectors.groupingBy(TrainingEvent::getChartId, Collectors.counting()));

        Map<Long, Long> snapshotCountMap = snapshots.stream()
                .collect(Collectors.groupingBy(ReportDocument::getChartId, Collectors.counting()));

        // 차트별 거래 리스트 (PnL 계산용)
        Map<Long, List<TrainingTrade>> tradesByChartId = trades.stream()
                .collect(Collectors.groupingBy(TrainingTrade::getChartId));

        // 5) 차트 요약 만들기
        // - 행동 + 결과(finalPnL) 포함
        List<SessionChartSummary> chartSummaries = charts.stream()
                .map(chart -> {
                    int tradeCount = tradeCountMap.getOrDefault(chart.getId(), 0L).intValue();
                    int eventCount = eventCountMap.getOrDefault(chart.getId(), 0L).intValue();
                    int snapshotCount = snapshotCountMap.getOrDefault(chart.getId(), 0L).intValue();

                    // 핵심: 차트별 손익 계산
                    BigDecimal finalPnL = calculateFinalPnL(
                            chart,
                            tradesByChartId.getOrDefault(chart.getId(), List.of())
                    );

                    return new SessionChartSummary(
                            chart.getId(),
                            chart.getChartIndex(),
                            chart.getSymbol().getTicker(),
                            chart.getSymbol().getName(),
                            chart.getStatus().name(),
                            chart.getProgressIndex(),
                            tradeCount,
                            eventCount,
                            snapshotCount,
                            tradeCount > 0,
                            finalPnL
                    );
                })
                .toList();

        // 6) snapshot은 있으면 요약해서 넣고, 없으면 빈 리스트
        List<SessionSnapshotSummary> snapshotSummaries = snapshots.stream()
                .map(doc -> new SessionSnapshotSummary(
                        doc.getChartId(),
                        doc.getVersion(),
                        text(doc.getContentJson(), "thesis"),
                        text(doc.getContentJson(), "entryReason"),
                        text(doc.getContentJson(), "exitPlan"),
                        text(doc.getContentJson(), "riskNote"),
                        text(doc.getContentJson(), "freeNote")
                ))
                .toList();

        // 완료된 차트 개수 계산
        int completedChartCount = (int) charts.stream()
                .filter(c -> "COMPLETED".equals(c.getStatus().name()))
                .count();

        // AI 요청 DTO 생성
        SessionAiAnalysisRequest request = new SessionAiAnalysisRequest(
                session.getId(),
                session.getAccount().getId(),
                session.getMode().name(),
                session.getStatus().name(),
                charts.size(),
                completedChartCount,
                trades.size(),
                events.size(),
                chartSummaries,
                snapshotSummaries
        );

        // 7) AI 분석 실행
        AiAnalysisResponse ai = aiAnalysisService.analyzeSession(request);

        // 8) payload 저장
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("analysisScope", "SESSION");
        payload.put("sessionId", session.getId());
        payload.put("score", ai.score());
        payload.put("summary", ai.summary());
        payload.put("totalChartCount", charts.size());
        payload.put("completedChartCount", completedChartCount);
        payload.put("totalTradeCount", trades.size());
        payload.put("totalEventCount", events.size());
        payload.put("snapshotCount", snapshots.size());

        // 경고 목록
        ArrayNode warningsNode = payload.putArray("warnings");
        if (ai.warnings() != null) {
            ai.warnings().forEach(warningsNode::add);
        }

        // 강점 목록
        ArrayNode strengthsNode = payload.putArray("strengths");
        if (ai.strengths() != null) {
            ai.strengths().forEach(strengthsNode::add);
        }

        // chartId null 불가라 첫 차트를 대표 chartId로 사용
        Long representativeChartId = charts.get(0).getId();

        // 이벤트 로그로 저장
        return trainingEventService.append(
                userId,
                representativeChartId,
                Type.AI,
                "세션 AI 리뷰: " + ai.summary(),
                payload
        );
    }

    /**
     * 차트 단위 최종 손익 계산
     *
     * 구성:
     * - realizedPnL: 매도 시 확정 손익
     * - unrealizedPnL: 아직 보유 중인 포지션의 평가 손익
     *
     * 계산 방식:
     * 1) 거래 로그를 순회하면서
     *    - BUY: 평균단가(avgPrice) 갱신
     *    - SELL: (판매가 - 평균단가) * 수량 → realizedPnL
     *
     * 2) 마지막에 포지션이 남아 있으면
     *    - 현재가 기준으로 미실현 손익 계산
     *
     * 특징:
     * - 차트 단위 독립 계산 (멀티차트 대응)
     * - 세션 종료 시점 기준 평가
     */
    private BigDecimal calculateFinalPnL(TrainingSessionChart chart, List<TrainingTrade> chartTrades) {

        // 거래 없으면 손익 0
        if (chartTrades == null || chartTrades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal realizedPnL = BigDecimal.ZERO; // 확정 손익
        BigDecimal positionQty = BigDecimal.ZERO; // 현재 보유 수량
        BigDecimal avgPrice = BigDecimal.ZERO;    // 평균 단가

        // 거래 순회 (시간 순)
        for (TrainingTrade trade : chartTrades) {
            BigDecimal qty = trade.getQty();
            BigDecimal price = trade.getPrice();

            // 매수
            if (trade.getSide() == TradeSide.BUY) {
                BigDecimal newQty = positionQty.add(qty);

                // 가중평균으로 avgPrice 계산
                if (newQty.compareTo(BigDecimal.ZERO) > 0) {
                    avgPrice = avgPrice.multiply(positionQty)
                            .add(price.multiply(qty))
                            .divide(newQty, 4, RoundingMode.HALF_UP);
                }

                positionQty = newQty;
                continue;
            }

            // 매도
            if (trade.getSide() == TradeSide.SELL) {

                // 실현 손익 계산
                BigDecimal pnl = price.subtract(avgPrice).multiply(qty);
                realizedPnL = realizedPnL.add(pnl);

                positionQty = positionQty.subtract(qty);

                // 전량 매도 시 avgPrice 초기화
                if (positionQty.compareTo(BigDecimal.ZERO) == 0) {
                    avgPrice = BigDecimal.ZERO;
                }
            }
        }

        BigDecimal unrealizedPnL = BigDecimal.ZERO;

        // 포지션 남아 있으면 미실현 손익 계산
        if (positionQty.compareTo(BigDecimal.ZERO) > 0) {

            int idx = (chart.getProgressIndex() == null) ? 0 : chart.getProgressIndex();
            int maxIdx = Math.max(0, chart.getBars() - 1);
            idx = Math.max(0, Math.min(idx, maxIdx));

            // 현재가 조회 (마지막 공개된 캔들)
            TrainingSessionCandle candle = candleRepository.findByChartIdAndIdx(chart.getId(), idx)
                    .orElseThrow(() -> new CustomException(ErrorCode.CANDLES_EMPTY));

            BigDecimal currentPrice = BigDecimal.valueOf(candle.getC());

            // 평가 손익 계산
            unrealizedPnL = currentPrice.subtract(avgPrice).multiply(positionQty);
        }

        // 최종 손익 = 실현 + 미실현
        return realizedPnL.add(unrealizedPnL).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * userId의 세션에 대한 '세션 단위 AI 분석 결과'를 조회.
     */
    @Transactional
    public TrainingEventResponse getLatestSessionAi(Long userId, Long sessionId) {
        // 1) 세션 조회 + 소유권 검증
        // - 해당 usrId의 세션인지 확인
        TrainingSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // 2) 세션에 포함된 차트 목록 조회
        List<TrainingSessionChart> charts = chartRepository.findAllBySession_IdOrderByChartIndexAsc(session.getId());

        // 차트가 없으면 AI 결과도 없음
        if (charts.isEmpty()) {
            return null;
        }

        // chartId 리스트 추출
        List<Long> chartIds = charts.stream()
                .map(TrainingSessionChart::getId)
                .toList();

        // 3) 해당 차트들에 대한 AI 이벤트 조회 (최신순)
        List<TrainingEvent> aiEvents = eventRepository
                .findAllByUserIdAndChartIdInAndTypeOrderByIdDesc(userId, chartIds, Type.AI);

        // 4) payload 기준으로 "세션 AI"만 필터링
        TrainingEvent matched = aiEvents.stream()
                .filter(event -> {

                    // payload JSON 가져오기
                    JsonNode payload = event.getPayloadJson();
                    if (payload == null || payload.isNull()) return false;

                    // analysisScope (SESSION / CHART 구분용)
                    String scope = payload.path("analysisScope").asText("");

                    // payload에 저장된 sessionId
                    long payloadSessionId = payload.path("sessionId").asLong(-1L);

                    // 조건:
                    // 1) 세션 분석인지
                    // 2) 현재 요청한 sessionId와 동일한지
                    return "SESSION".equals(scope) && payloadSessionId == sessionId;
                })
                .findFirst()// 최신순이므로 첫 번째가 가장 최신
                .orElse(null);

        // 5) 매칭되는 AI 결과가 없으면 null 반환
        if (matched == null) {
            throw new CustomException(ErrorCode.SESSION_AI_NOT_FOUND);
        }

        // 6) TrainingEvent + Response DTO 변환
        return new TrainingEventResponse(
                matched.getId(),
                matched.getChartId(),
                matched.getType().name(),
                matched.getSummary(),
                matched.getPayloadJson(),
                matched.getCreatedAt()
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isNull()) return "";
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) return "";
        return child.asText("");
    }
}
