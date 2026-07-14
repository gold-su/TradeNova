package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.entity.PaperPosition;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.report.entity.Type;
import com.tradenova.report.service.TrainingEventService;
import com.tradenova.training.dto.SessionProgressResponse;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor    //final 필드 자동 생성자
public class TrainingSessionProgressService {

    // 훈련 세션 DB 접근용 레포지토리
    private final TrainingSessionChartRepository chartRepo;
    // 자동청산(리스크 룰) 판단 로직을 담당하는 서비스
    private final TrainingAutoExitService autoExitService;

    private final PaperPositionRepository positionRepo;

    // 자동청산 발생 시 실제 전량매도 처리용
    private final TrainingTradeService tradeService;

    private final TrainingEventService eventService;
    private final ObjectMapper objectMapper; // payload 만들 때 편함

    private final TrainingSessionCandleRepository candleRepo;

    /**
     * 한 봉(candle)만 진행시키는 API
     * - 내부적으로 advance(..., 1)을 호출
     */
    @Transactional //트랜잭션
    public SessionProgressResponse next(Long userId, Long chartId) {
        return advance(userId, chartId, 1);
    }


    /**
     * N 봉을 한 번에 진행시키는 메서드
     * - userId로 세션 소유권 검증
     * - progressIndex 증가
     * - 종료 조건 체크
     * - 자동청산(리스크 룰) 검사
     */
    @Transactional
    public SessionProgressResponse advance(Long userId, Long chartId, int steps){

        // 1) 세션 조회 + 소유권 검증
        //      - 남의 세션 접근 방지
        TrainingSessionChart chart = chartRepo.findForUpdateByIdAndUserId(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND)); // 없으면 새 에러코드 추천

        // 2) 세션 상태 검사 (정책: 세션이 완료면 차트 진행 금지)
        if (chart.getSession().getStatus() != TrainingStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        // 차트가 COMPLETED 상태면 에러
        if (chart.getStatus() == TrainingChartStatus.COMPLETED) {
            throw new CustomException(ErrorCode.TRAINING_CHART_ALREADY_COMPLETED);
        }
        
        // 3) steps 검증
        //    - 0 이하로 넘기는 건 의미 없으므로 거부
        // 스펙: 1 ~ 500
        if (steps < 1 || steps > 500) {
            throw new CustomException(ErrorCode.INVALID_ADVANCE_STEPS);
        }

        // 4) idx 범위 계산
        //    예: bars = 100 -> maxIdx = 99
        int maxIdx = Math.max(0, chart.getBars() - 1);
        //    - 아직 한 번도 진행 안 했으면 null -> 0으로 처리
        int cur = (chart.getProgressIndex() == null) ? 0 : chart.getProgressIndex();
        //    다음 progressIndex 계산
        //    - cur + steps 만큼 앞으로 가되
        //    - 마지막 캔들(maxIdx)을 넘지 않도록 보정
        int nextIdx = Math.min(cur + steps, maxIdx);

        // 5) 진행 반영
        chart.setProgressIndex(nextIdx);

        // currentPrice는 아래에서 계산되니 일단 나중에 put해도 됨

        // 여기서 flush 한번 걸어주면 이후 로직에서 progressIndex 기준 조회가 안정적
        // (candle 조회/autoExit/거래 로직이 같은 트랜잭션에서 일관되게 동작)
        chartRepo.flush();

        // 6) 자동청산 체크 (반환: autoExited 여부 + reason + currentPrice)
        //    - stop loss / take profit 조건 충족 여부 판단
        //    - 지금 단계에서는 "실제 매도"가 아니라
        //    - autoExited 여부와 사유만 계산
        TrainingAutoExitService.AutoExitResult r =
                autoExitService.checkAndAutoExit(chart.getId(), chart);

        // 7) 현재가 (progressIndex 기준 close 가격)
        BigDecimal currentPrice = r.currentPrice();

        // progress 이벤트 로그 payload
        ObjectNode progressPayload  = objectMapper.createObjectNode();
        progressPayload .putPOJO("steps", steps);
        progressPayload .putPOJO("progressIndex", chart.getProgressIndex());
        progressPayload .putPOJO("bars", chart.getBars());
        // 가격 추가
        progressPayload .putPOJO("currentPrice", currentPrice);

        // 8) 스냅샷 구성
        Long accountId = chart.getSession().getAccount().getId();
        Long symbolId = chart.getSymbol().getId();

        PaperPosition pos = positionRepo.findByAccountIdAndSymbolId(accountId, symbolId)
                .orElse(null);

        BigDecimal positionQty = (pos == null) ? BigDecimal.ZERO : pos.getQuantity();
        BigDecimal avgPrice = (pos == null) ? BigDecimal.ZERO : pos.getAvgPrice();
        // 현금 (PaperAccount에 맞는 getter로 바꾸기)
        BigDecimal cashBalance = chart.getSession().getAccount().getCashBalance();


        // 9) 자동청산
        //     - 룰은 발동했는데 포지션이 0이면, 팔 게 없으니 자동청산 실행은 스킵(UX 깔끔)
        boolean executedAutoExit = false;
        var autoExitReason = r.reason();

        // WARNING 이벤트는 바로 저장하지 말고, payload만 준비
        ObjectNode autoExitPayload = null;
        String autoExitSummary = null;

        if (r.autoExited() && positionQty.compareTo(BigDecimal.ZERO) > 0) {
            TradeResponse sellAllResult = tradeService.sellAll(userId, chart.getId());

            // sellAll 결과로 스냅샷 갱신
            cashBalance = sellAllResult.cashBalance();
            positionQty = sellAllResult.positionQty();
            avgPrice = sellAllResult.avgPrice();

            // 체결가도 sellAllResult.executedPrice()를 써도 되지만,
            // 여기선 progress에서 계산한 currentPrice와 동일하게 맞춰도 OK
            currentPrice = sellAllResult.executedPrice();

            executedAutoExit = true;

            // warning payload만 준비
            autoExitPayload = objectMapper.createObjectNode();
            autoExitPayload.putPOJO("reason", autoExitReason.name());
            autoExitPayload.putPOJO("executedPrice", currentPrice);
            autoExitPayload.putPOJO("chartId", chart.getId());

            autoExitSummary = "자동청산 발생: " + autoExitReason.name();


        }

        // 자동청산 결과를 progress payload에 반영
        progressPayload.putPOJO("autoExited", executedAutoExit);
        progressPayload.putPOJO("autoExitReason", autoExitReason == null ? null : autoExitReason.name());
        progressPayload.putPOJO("currentPrice", currentPrice); // 혹시 autoExit 후 체결가로 바뀌었다면 최종값 반영

        // 10) chart가 마지막 봉까지 가면(옵션) 세션까지 종료할지?
        /*
         * 마지막 봉에 도달하면 차트만 완료한다.
         *
         * 중요:
         * 세션은 여기서 자동 완료하지 않는다.
         *
         * 이유:
         * - 사용자가 마지막 봉 이후 매매 기록을 복기해야 함
         * - 차트/세션 AI 리뷰를 생성해야 함
         * - 스냅샷과 메모를 작성할 수 있어야 함
         * - 사용자가 직접 '훈련 종료'를 눌렀을 때 세션을 완료해야 함
         */
        if (nextIdx >= maxIdx) {
            chart.complete();
        }

        // 1) 먼저 progress 저장
        eventService.append(
                userId,
                chart.getId(),
                Type.PROGRESS,
                steps + "봉 진행",
                progressPayload
        );

        // 2) 그 다음 warning 저장
        if (executedAutoExit) {
            eventService.append(
                    userId,
                    chart.getId(),
                    Type.WARNING,
                    autoExitSummary,
                    autoExitPayload
            );
        }


        int finalMaxIndex =
                Math.max(0, chart.getBars() - 1);

        int remainingBars =
                Math.max(
                        0,
                        finalMaxIndex - chart.getProgressIndex()
                );

        boolean atLastBar =
                chart.getProgressIndex() >= finalMaxIndex;

        // 11) 프론트로 내려줄 진행 결과 응답 DTO 생성
        return new SessionProgressResponse(
                chart.getId(),
                chart.getProgressIndex(),
                finalMaxIndex,
                remainingBars,
                atLastBar,
                currentPrice,
                chart.getStatus().name(),
                chart.getSession().getStatus().name(),
                cashBalance,
                positionQty,
                avgPrice,
                executedAutoExit,
                autoExitReason
        );
    }

    /**
     * 현재 차트 진행 상태 조회
     *
     * 사용처:
     * - 훈련 페이지 새로고침
     * - 진행 중 세션 복구
     * - 차트 변경 시 계좌/포지션 최신화
     */
    @Transactional(readOnly = true)
    public SessionProgressResponse getProgress(
            Long userId,
            Long chartId
    ) {
        // 1. 차트 조회 및 소유권 검증
        TrainingSessionChart chart =
                chartRepo.findByIdAndSession_User_Id(chartId, userId)
                        .orElseThrow(()->
                                new CustomException(
                                        ErrorCode.TRAINING_CHART_NOT_FOUND
                                )
                        );
        // 2. 현재 진행 위치 계산
        int progressIndex =
                chart.getProgressIndex() == null
                        ? 0
                        : chart.getProgressIndex();

        int maxIndex = Math.max(0, chart.getBars() - 1);

        // 방어적으로 범위를 보정
        int safeProgressIndex =
                Math.min(Math.max(progressIndex, 0), maxIndex);

        int remainingBars =
                Math.max(0, maxIndex - safeProgressIndex);

        boolean atLastBar =
                safeProgressIndex >= maxIndex;

        // 3. 현재 공개된 마지막 캔들의 종가 조회
        TrainingSessionCandle currentCandle =
                candleRepo.findByChartIdAndIdx(
                        chart.getId(),
                        safeProgressIndex
                )
                        .orElseThrow(()->
                                new CustomException(
                                        ErrorCode.CANDLES_EMPTY
                                )
                        );

        BigDecimal currentPrice =
                BigDecimal.valueOf(currentCandle.getC());

        // 4. 계좌 상태 조회
        PaperAccount account =
                chart.getSession().getAccount();

        BigDecimal cashBalance =
                account.getCashBalance() == null
                        ? BigDecimal.ZERO
                        : account.getCashBalance();

        // 5. 현재 차트 종목의 포지션 조회
        PaperPosition position =
                positionRepo
                        .findByAccountIdAndSymbolId(
                                account.getId(),
                                chart.getSymbol().getId()
                        )
                        .orElse(null);

        BigDecimal positionQty =
                position == null || position.getQuantity() == null
                        ? BigDecimal.ZERO
                        : position.getQuantity();

        BigDecimal avgPrice =
                position == null || position.getAvgPrice() == null
                ? BigDecimal.ZERO
                : position.getAvgPrice();

        // 6. 현재 상태 반환
        // 조회 API이므로 autoExited=false, reason=null
        return new SessionProgressResponse(
                chart.getId(),
                safeProgressIndex,
                maxIndex,
                remainingBars,
                atLastBar,
                currentPrice,
                chart.getStatus().name(),
                chart.getSession().getStatus().name(),
                cashBalance,
                positionQty,
                avgPrice,
                false,
                null
        );
    }
}
