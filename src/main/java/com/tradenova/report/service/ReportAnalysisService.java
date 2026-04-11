package com.tradenova.report.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.paper.entity.PaperPosition;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.AiAnalysisResponse;
import com.tradenova.report.dto.TrainingEventResponse;
import com.tradenova.report.entity.ReportDocument;
import com.tradenova.report.entity.ReportKind;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.report.repository.ReportDocumentRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.training.entity.TrainingRiskRule;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingTrade;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 리포트 AI 분석 orchestration 서비스
 *
 * 역할
 * - 최신 draft/snapshot 조회
 * - 차트/캔들/트레이드 데이터 결합
 * - AiAnalysisService 호출
 * - AI 결과를 training_event로 저장
 *
 * 핵심 원칙
 * - "분석 흐름 전체"를 담당
 * - OpenAI 직접 호출은 AiAnalysisService에 위임
 */
@Service
@RequiredArgsConstructor
public class ReportAnalysisService {

    // 리포트 문서(snapshot/draft) 조회용
    private final ReportDocumentRepository reportDocumentRepository;

    // 차트 소유권 검증 및 차트 조회용
    private final TrainingSessionChartRepository chartRepository;

    // 최근 캔들 데이터 조회용
    private final TrainingSessionCandleRepository candleRepository;


    // 최근 체결 데이터 조회용
    private final TrainingTradeRepository tradeRepository;

    // 실제 OpenAI 호출 담당
    private final AiAnalysisService aiAnalysisService;

    // 리스크 룰 조회용
    private final TrainingRiskRuleRepository trainingRiskRuleRepository;

    // AI 분석 결과를 이벤트 로그로 저장
    private final TrainingEventService trainingEventService;

    // payload JSON 생성용
    private final ObjectMapper objectMapper;
    
    // paper DB 가져오기
    private final PaperPositionRepository paperPositionRepository;

    // Event Repo 가져오기
    private final TrainingEventRepository trainingEventRepository;

    /**
     * 특정 차트의 최신 snapshot을 분석해서
     * AI 리뷰 이벤트를 생성한다.
     *
     * 흐름:
     * 1. 차트 소유권 검증
     * 2. 최신 snapshot 조회
     * 3. snapshot 내용 검사
     * 4. 최근 캔들/거래 데이터 수집
     * 5. AI 요청 DTO 생성
     * 6. AI 분석 호출
     * 7. 결과를 training_event로 저장
     */
    @Transactional
    public TrainingEventResponse analyzeLatestSnapshot(Long userId, Long chartId) {

        // 1) 차트 소유권 검증
        TrainingSessionChart chart = chartRepository.findByIdAndSession_User_Id(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // 이미 생성된 차트 AI가 있으면 중복 생성 방지
        TrainingEvent existing = findLatestChartAiEventOrNull(userId, chartId);
        if (existing != null) {
            throw new CustomException(ErrorCode.CHART_AI_ALREADY_EXISTS);
        }

        // 2) 해당 유저/차트의 최신 snapshot 리포트 조회
        ReportDocument snapshot = reportDocumentRepository
                .findTopByUserIdAndChartIdAndKindOrderByVersionDesc(userId, chartId, ReportKind.SNAPSHOT)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_SNAPSHOT_NOT_FOUND));


        // 3) snapshot 본문 JSON 꺼내기
        JsonNode content = snapshot.getContentJson();
        if (content == null || content.isNull()) {
            throw new CustomException(ErrorCode.REPORT_CONTENT_EMPTY);
        }

        // 4) 최근 캔들 30개 조회
        List<TrainingSessionCandle> candles = candleRepository.findTop30ByChartIdOrderByIdxDesc(chartId);

        if (candles == null || candles.isEmpty()) {
            throw new CustomException(ErrorCode.CANDLES_EMPTY);
        }

        // 종가 리스트 추출
        List<Double> closes = candles.stream()
                .map(TrainingSessionCandle::getC)
                .toList();

        // 거래량 리스트 추출
        List<Double> volumes = candles.stream()
                .map(TrainingSessionCandle::getV)
                .toList();

        // 5) 최근 거래 1건 조회 (없을 수도 있음)
        TrainingTrade latestTrade = tradeRepository
                .findTopByChartIdOrderByIdDesc(chartId)
                .orElse(null);

        // 최근 거래가 없으면 0으로 대체
        BigDecimal price = latestTrade != null ? latestTrade.getPrice() : BigDecimal.ZERO;
        BigDecimal qty = latestTrade != null ? latestTrade.getQty() : BigDecimal.ZERO;

        // 6) 현재 포지션/계좌 정보 조회
        Long accountId = chart.getSession().getAccount().getId();
        Long symbolId = chart.getSymbol().getId();

        // 해당 계좌 + 종목 포지션 조회
        PaperPosition position = paperPositionRepository
                .findByAccountIdAndSymbolId(accountId, symbolId)
                .orElse(null);

        // 평균 매수가, 없으면 평균가 0
        BigDecimal avgPrice = position != null ? position.getAvgPrice() : BigDecimal.ZERO;

        // 보유 수량, 없으면 수량 0
        BigDecimal positionQty = position != null ? position.getQuantity() : BigDecimal.ZERO;

        // 현재 계좌의 남은 현금
        BigDecimal cashBalance = chart.getSession().getAccount().getCashBalance();

        // 7) 리스크 룰 조회
        TrainingRiskRule riskRule = trainingRiskRuleRepository.findByChartId(chartId)
                .orElse(null);

        BigDecimal stopLossPrice = riskRule != null ? riskRule.getStopLossPrice() : null;
        BigDecimal takeProfitPrice = riskRule != null ? riskRule.getTakeProfitPrice() : null;
        Boolean autoExitEnabled = riskRule != null ? riskRule.isEnabled() : Boolean.FALSE;


        // 7) AI 분석 요청 DTO 생성
        AiAnalysisRequest request = new AiAnalysisRequest(
                text(content, "thesis"),
                text(content, "entryReason"),
                text(content, "exitPlan"),
                text(content, "riskNote"),
                text(content, "freeNote"),
                price,
                qty,
                avgPrice,
                positionQty,
                cashBalance,
                stopLossPrice,
                takeProfitPrice,
                autoExitEnabled,
                closes,
                volumes
        );

        // 8) AI 분석 실행
        AiAnalysisResponse ai = aiAnalysisService.analyze(request);

        // 9) AI 결과를 training_event payload JSON 구성
        ObjectNode payload = objectMapper.createObjectNode();

        payload.put("analysisScope", "CHART");

        payload.put("score", ai.score());
        payload.put("summary", ai.summary());

        payload.put("generatedAt", java.time.Instant.now().toString());
        payload.put("analysisVersion", 1);

        ArrayNode warningsNode = payload.putArray("warnings");
        if (ai.warnings() != null) {
            ai.warnings().forEach(warningsNode::add);
        }

        ArrayNode strengthsNode = payload.putArray("strengths");
        if (ai.strengths() != null) {
            ai.strengths().forEach(strengthsNode::add);
        }

        // 어떤 snapshot / chart에 대한 결과인지 함께 저장
        // 분석에 사용된 문맥도 일부 같이 저장
        payload.put("snapshotId", snapshot.getId());
        payload.put("chartId", chartId);
        payload.put("stopLossPrice", stopLossPrice != null ? stopLossPrice.toPlainString() : null);
        payload.put("takeProfitPrice", takeProfitPrice != null ? takeProfitPrice.toPlainString() : null);
        payload.put("autoExitEnabled", autoExitEnabled);

        // 10) AI 리뷰 결과를 training_event로 저장 후 반환
        return trainingEventService.append(
                userId,
                chartId,
                Type.AI,
                "차트 AI 리뷰",
                payload
        );
    }

    /**
     * 특정 차트의 최신 차트 AI 결과 조회
     */
    @Transactional
    public TrainingEventResponse getLatestChartAi(Long userId, Long chartId) {

        // 1) 차트 소유권 검증
        chartRepository.findByIdAndSession_User_Id(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // 2) 내부 조회 메서드 호출 (있으면 TrainingEvent, 없으면 null)
        TrainingEvent matched = findLatestChartAiEventOrNull(userId, chartId);

        // 3) 결과가 없으면 차트 AI 분석 자체가 없는 상태 + 404 처리
        if (matched == null) {
            throw new CustomException(ErrorCode.CHART_AI_NOT_FOUND);
        }

        // 4) 엔티티 -> 응답 DTO 변환
        // - 프론트에서 바로 사용할 수 있는 형태로 변환
        return new TrainingEventResponse(
                matched.getId(),            // 이벤트 ID
                matched.getChartId(),       // 차트 ID
                matched.getType().name(),   // 타입 (AI)
                matched.getSummary(),       // 한 줄 요약
                matched.getPayloadJson(),   // 상세 분석 JSON
                matched.getCreatedAt()      // 생성 시간
        );
    }

    /**
     * 특정 차트의 최신 차트 AI 이벤트를 조회한다.
     * - 있으면 TrainingEvent 반환
     * - 없으면 null 반환
     */
    private TrainingEvent findLatestChartAiEventOrNull(Long userId, Long chartId) {
        // 1) 해당 차트의 AI 이벤트 목록 조회 (최신순)
        List<TrainingEvent> aiEvents = trainingEventRepository
                .findAllByUserIdAndChartIdAndTypeOrderByIdDesc(userId, chartId, Type.AI);

        // 2) payload 기준으로 "차트 AI"만 필터링
        return aiEvents.stream()
                .filter(event -> {

                    // payload JSON 가져오기
                    JsonNode payload = event.getPayloadJson();

                    // payload가 없으면 무조건 제외
                    if (payload == null || payload.isNull()) return false;

                    // analysisScope -> "CHART" / "SESSION" 구분용
                    String scope = payload.path("analysisScope").asText("");

                    // payload 내부에 저장된 chartId
                    long payloadChartId = payload.path("chartId").asLong(-1L);

                    // 조건:
                    // 1) 차트 분석인지 (CHART)
                    // 2) 해당 chartId와 일치하는지
                    return "CHART".equals(scope) && payloadChartId == chartId;
                })
                // 최신순으로 가져왔기 때문에 첫 번째가 가장 최신 결과
                .findFirst()
                // 없으면 null 반환 (내부 로직용)
                .orElse(null);
    }

    /**
     * JsonNode에서 문자열 안전 추출
     * - null이면 빈 문자열 반환
     * - 필드가 없거나 null이어도 빈 문자열 반환
     */
    private String text(JsonNode node, String field) {
        if (node == null || node.isNull()) return "";
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) return "";
        return child.asText("");
    }
}
