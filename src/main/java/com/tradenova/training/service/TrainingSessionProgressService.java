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
import com.tradenova.training.dto.AutoExitReason;
import com.tradenova.training.dto.SessionProgressResponse;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
 * N개의 봉을 한 번에 진행한다.
 *
 * - 차트/세션 상태 검증
 * - 요청한 steps만큼 봉을 하나씩 진행
 * - 각 봉마다 손절/익절 조건 검사
 * - 자동청산 발생 시 해당 봉에서 진행 중단
 * - 최종 계좌/포지션 상태와 진행 결과 반환
 */
    @Transactional
    public SessionProgressResponse advance(
            Long userId,
            Long chartId,
            int steps
    ) {

        // 차트 조회 + 사용자 소유권 검증 + 동시 수정 방지를 위한 Lock
        TrainingSessionChart chart =
                chartRepo.findForUpdateByIdAndUserId(chartId, userId)
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.TRAINING_CHART_NOT_FOUND
                                )
                        );

        // 세션이 진행 중이 아니면 차트를 진행할 수 없음
        if (chart.getSession().getStatus()
                != TrainingStatus.IN_PROGRESS) {

            throw new CustomException(
                    ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS
            );
        }

        // 이미 마지막 봉까지 진행된 차트인지 확인
        if (chart.getStatus()
                == TrainingChartStatus.COMPLETED) {

            throw new CustomException(
                    ErrorCode.TRAINING_CHART_ALREADY_COMPLETED
            );
        }

        // 한 번에 진행 가능한 봉 수는 1 ~ 500
        if (steps < 1 || steps > 500) {
            throw new CustomException(
                    ErrorCode.INVALID_ADVANCE_STEPS
            );
        }


        // 마지막 캔들 인덱스
        // 예: bars = 100이면 idx는 0 ~ 99이므로 maxIdx = 99
        int maxIdx =
                Math.max(0, chart.getBars() - 1);


        // progressIndex가 null이면 0부터 시작
        // 조건 ? A : B = 조건이 참이면 A, 아니면 B
        int rawProgressIndex =
                chart.getProgressIndex() == null
                        ? 0
                        : chart.getProgressIndex();


        // 현재 인덱스를 0 ~ maxIdx 범위 안으로 보정
        int cur =
                Math.min(
                        Math.max(rawProgressIndex, 0),
                        maxIdx
                );


        // 사용자가 요청한 목표 위치
        // 마지막 봉(maxIdx)을 넘어가지 않도록 제한
        int targetIdx =
                Math.min(
                        cur + steps,
                        maxIdx
                );


        // 실제로 도달한 최종 위치
        // 자동청산이 중간에 발생할 수 있으므로 targetIdx와 따로 관리
        int finalIdx = cur;

        boolean executedAutoExit = false;

        AutoExitReason autoExitReason = null;

        TradeResponse autoExitTrade = null;

        ObjectNode autoExitPayload = null;
        String autoExitSummary = null;


        // 현재 위치의 캔들을 조회해서 시작 현재가를 구함
        TrainingSessionCandle initialCandle =
                candleRepo
                        .findByChartIdAndIdx(
                                chart.getId(),
                                cur
                        )
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.CANDLES_EMPTY
                                )
                        );


        // valueOf(): 숫자를 BigDecimal로 변환
        BigDecimal currentPrice =
                BigDecimal.valueOf(
                        initialCandle.getC()
                );


        /*
         * 요청한 위치로 한 번에 점프하지 않고
         * 한 봉씩 진행하면서 자동청산 조건을 검사한다.
         *
         * 예:
         * cur = 10, targetIdx = 20이면
         * 11 -> 12 -> ... -> 20 순서로 검사
         */
        for (int idx = cur + 1;
             idx <= targetIdx;
             idx++) {

            // 현재 진행할 봉 조회
            TrainingSessionCandle candle =
                    candleRepo
                            .findByChartIdAndIdx(
                                    chart.getId(),
                                    idx
                            )
                            .orElseThrow(() ->
                                    new CustomException(
                                            ErrorCode.CANDLES_EMPTY
                                    )
                            );


            // 실제로 현재 봉까지 진행
            chart.setProgressIndex(idx);
            finalIdx = idx;


            // 현재 봉 종가를 현재가로 사용
            currentPrice =
                    BigDecimal.valueOf(
                            candle.getC()
                    );


            // 현재 봉의 high/low/open을 기준으로 손절·익절 여부 검사
            TrainingAutoExitService.AutoExitResult decision =
                    autoExitService.checkAndAutoExit(
                            chart.getId(),
                            candle
                    );


            // 자동청산 조건이 아니면 다음 봉으로 진행
            if (!decision.autoExited()) {
                continue;
            }


            // STOP_LOSS 또는 TAKE_PROFIT
            autoExitReason =
                    decision.reason();


            /*
             * 실제 자동청산 실행
             *
             * AutoExitService가 계산한 체결가와
             * 자동청산이 발생한 캔들 시간을 넘겨준다.
             */
            TrainingTradeService.LockedSellResult lockedSell =
                    tradeService.sellAllAtPriceLockedResult(
                            userId,
                            chart,
                            decision.executedPrice(),
                            candle.getT(),
                            autoExitReason
                    );

            autoExitTrade = lockedSell.response();
            if (autoExitTrade.tradeId() == null) {
                continue;
            }
            BigDecimal exitQty = lockedSell.executedQty();


            // 자동청산이 발생한 경우 실제 체결가를 현재가로 사용
            currentPrice =
                    autoExitTrade.executedPrice();

            executedAutoExit = true;


            // enum의 이름을 문자열로 변환
            // 예: STOP_LOSS -> "STOP_LOSS"
            String reasonName =
                    autoExitReason == null
                            ? "UNKNOWN"
                            : autoExitReason.name();


            // 자동청산 WARNING 이벤트 payload
            autoExitPayload =
                    objectMapper.createObjectNode();

            autoExitPayload.put(
                    "reason",
                    reasonName
            );

            autoExitPayload.putPOJO(
                    "tradeId",
                    autoExitTrade.tradeId()
            );

            autoExitPayload.putPOJO(
                    "qty",
                    exitQty
            );

            autoExitPayload.putPOJO(
                    "executedPrice",
                    autoExitTrade.executedPrice()
            );

            autoExitPayload.putPOJO(
                    "candleTime",
                    candle.getT()
            );

            autoExitPayload.put(
                    "chartId",
                    chart.getId()
            );

            autoExitSummary =
                    "자동청산 발생: "
                            + reasonName;


            /*
             * 최초 자동청산이 발생한 봉에서 진행 중단
             *
             * break는 for문 자체를 종료한다.
             */
            break;
        }


        // 실제로 도달한 최종 위치 저장
        chart.setProgressIndex(finalIdx);


        // 실제 진행된 봉 개수
        // 자동청산으로 중간에 멈추면 요청한 steps보다 작을 수 있음
        int advancedSteps =
                finalIdx - cur;


        /*
         * 마지막 봉에 도달했고 손절/익절 청산이 이미 발생하지 않았다면
         * 마지막 봉의 종가로 남은 포지션을 전량 청산한다.
         * advance 트랜잭션이 chart lock을 이미 보유하므로 lock 재조회는 하지 않는다.
         */
        if (finalIdx >= maxIdx) {
            if (!executedAutoExit) {
                TrainingSessionCandle lastCandle =
                        candleRepo.findByChartIdAndIdx(chart.getId(), finalIdx)
                                .orElseThrow(() -> new CustomException(ErrorCode.CANDLES_EMPTY));

                BigDecimal exitPrice = BigDecimal.valueOf(lastCandle.getC());
                autoExitReason = AutoExitReason.END_OF_CHART;
                TrainingTradeService.LockedSellResult lockedSell =
                        tradeService.sellAllAtPriceLockedResult(
                                userId,
                                chart,
                                exitPrice,
                                lastCandle.getT(),
                                autoExitReason
                        );
                autoExitTrade = lockedSell.response();

                if (autoExitTrade.tradeId() != null) {
                    BigDecimal exitQty = lockedSell.executedQty();
                    currentPrice = autoExitTrade.executedPrice();
                    executedAutoExit = true;

                    autoExitPayload = objectMapper.createObjectNode();
                    autoExitPayload.put("reason", autoExitReason.name());
                    autoExitPayload.putPOJO("tradeId", autoExitTrade.tradeId());
                    autoExitPayload.putPOJO("qty", exitQty);
                    autoExitPayload.putPOJO("executedPrice", autoExitTrade.executedPrice());
                    autoExitPayload.putPOJO("candleTime", lastCandle.getT());
                    autoExitPayload.put("chartId", chart.getId());
                    autoExitSummary = "마지막 봉 강제청산 발생: " + autoExitReason.name();
                }
            }

            // 잔여 포지션 청산이 완료된 뒤에 차트를 완료 상태로 변경한다.
            chart.complete();
        }


        // 현재 엔티티 변경사항을 DB에 반영
        // commit은 아니고 트랜잭션 안에서 SQL을 미리 실행하는 것
        chartRepo.flush();


        // ==========================
        // 최종 계좌 / 포지션 스냅샷
        // ==========================

        PaperAccount account =
                chart.getSession()
                        .getAccount();

        Long accountId =
                account.getId();

        Long symbolId =
                chart.getSymbol()
                        .getId();


        // 자동청산까지 모두 끝난 후 최종 포지션을 다시 조회
        PaperPosition finalPosition =
                positionRepo
                        .findByAccountIdAndSymbolId(
                                accountId,
                                symbolId
                        )
                        .orElse(null);


        // 최종 보유 수량
        BigDecimal positionQty =
                finalPosition == null ||
                        finalPosition.getQuantity() == null
                        ? BigDecimal.ZERO
                        : finalPosition.getQuantity();


        // 최종 평균 매수가
        BigDecimal avgPrice =
                finalPosition == null ||
                        finalPosition.getAvgPrice() == null
                        ? BigDecimal.ZERO
                        : finalPosition.getAvgPrice();


        // 최종 현금 잔액
        BigDecimal cashBalance =
                autoExitTrade != null
                        ? autoExitTrade.cashBalance()
                        : account.getCashBalance() == null
                        ? BigDecimal.ZERO
                        : account.getCashBalance();


        // ==========================
        // PROGRESS 이벤트
        // ==========================

        ObjectNode progressPayload =
                objectMapper.createObjectNode();

        // 요청한 봉 수
        progressPayload.putPOJO(
                "requestedSteps",
                steps
        );

        // 실제 진행한 봉 수
        progressPayload.putPOJO(
                "advancedSteps",
                advancedSteps
        );

        progressPayload.putPOJO(
                "fromIndex",
                cur
        );

        // 자동청산 시 targetIdx가 아닌 실제 도착 위치 저장
        progressPayload.putPOJO(
                "toIndex",
                finalIdx
        );

        progressPayload.putPOJO(
                "progressIndex",
                finalIdx
        );

        progressPayload.putPOJO(
                "bars",
                chart.getBars()
        );

        progressPayload.putPOJO(
                "currentPrice",
                currentPrice
        );

        progressPayload.put(
                "autoExited",
                executedAutoExit
        );

        progressPayload.putPOJO(
                "autoExitReason",
                executedAutoExit &&
                        autoExitReason != null
                        ? autoExitReason.name()
                        : null
        );


        // 차트 진행 이벤트 저장
        eventService.append(
                userId,
                chart.getId(),
                Type.PROGRESS,
                advancedSteps + "봉 진행",
                progressPayload
        );


        // 실제 자동청산이 발생했다면 WARNING 이벤트도 저장
        if (executedAutoExit) {
            eventService.append(
                    userId,
                    chart.getId(),
                    Type.WARNING,
                    autoExitSummary,
                    autoExitPayload
            );
        }


        // 앞으로 남은 봉 개수
        int remainingBars =
                Math.max(
                        0,
                        maxIdx - finalIdx
                );


        // 마지막 봉 도달 여부
        boolean atLastBar =
                finalIdx >= maxIdx;


        // 최종 진행 상태를 프론트에 반환
        return new SessionProgressResponse(
                chart.getId(),
                finalIdx,
                maxIdx,
                remainingBars,
                atLastBar,
                currentPrice,
                chart.getStatus().name(),
                chart.getSession()
                        .getStatus()
                        .name(),
                cashBalance,
                positionQty,
                avgPrice,
                executedAutoExit,
                executedAutoExit
                        ? autoExitReason
                        : null
        );
    }



    /**
     * 현재 차트 진행 상태 조회
     *
     * 사용처:
     * - 훈련 페이지 새로고침
     * - 진행 중 세션 복구
     * - 차트 변경 시 계좌/포지션 최신화
     *
     * 이 메서드는 DB 상태를 변경하지 않는다.
     */
    @Transactional(readOnly = true)
    public SessionProgressResponse getProgress(
            Long userId,
            Long chartId
    ) {

        // 1. 차트 조회 및 소유권 검증
        TrainingSessionChart chart =
                chartRepo.findByIdAndSession_User_Id(chartId, userId)
                        .orElseThrow(() ->
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
                candleRepo
                        .findByChartIdAndIdx(
                                chart.getId(),
                                safeProgressIndex
                        )
                        .orElseThrow(() ->
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
