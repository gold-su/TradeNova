package com.tradenova.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.entity.PaperPosition;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.report.entity.Type;
import com.tradenova.report.service.TrainingEventService;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.dto.TrainingTradeItemResponse;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingRiskRuleHistoryRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.tradenova.training.dto.AutoExitReason;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingTradeService {

    // chart 기반 조회
    private final TrainingSessionChartRepository chartRepo;
    // 세션에 저장된 캔들 조회용 (현재가 계산에 사용)
    private final TrainingSessionCandleRepository candleRepo;
    // 훈련 매매 기록(TrainingTrader) 조회용
    private final TrainingTradeRepository tradeRepo;
    private final TrainingRiskRuleHistoryRepository riskHistoryRepo;
    // 페이퍼 계좌/포지션 관련 (현금/보유수량 갱신)
    private final PaperAccountRepository accountRepo;
    private final PaperPositionRepository positionRepo;

    private final TrainingEventService eventService;
    private final ObjectMapper objectMapper;

    /**
     * 매수(BUY)
     * - 세션 소유권 검증
     * - 현재가(진행 index 기반 close)로 cost 계산
     * - 현금 부족이면 예외
     * - 포지션 upsert(없으면 생성, 있으면 평단 갱신)
     * - 현금 차감
     * - trade 기록 저장
     * - TradeResponse 반환
     */
    @Transactional // 아래 작업들을 한 트랜잭션으로 묶음 (중간 실패 시 롤백)
    public TradeResponse buy(Long userId, Long chartId, BigDecimal qty){

        // chartId + userId(세션 소유자) 조건으로 차트를 조회
        // - session.user.id까지 조건에 포함해서 "남의 차트는 조회 자체가 안 되게" 막음(보안/치팅 방지)
        TrainingSessionChart chart = chartRepo.findForUpdateByIdAndUserId(chartId, userId)
                // 없으면 404 성격의 커스텀 예외(차트 없음 또는 남의 차트)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // ===== 세션 상태 검증 =====

        // 세션 정책: 세션이 진행중(IN_PROGRESS)이 아니면 차트 거래도 금지
        // - COMPLETED 등 종료 상태에서 매수/매도하면 정합성 깨짐
        if (chart.getSession().getStatus() != TrainingStatus.IN_PROGRESS) {
            // 세션이 진행 중이 아님 → 거래 불가 에러
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        // 차트가 COMPLETED 상태면 에러
        if (chart.getStatus() == TrainingChartStatus.COMPLETED) {
            throw new CustomException(ErrorCode.TRAINING_CHART_ALREADY_COMPLETED);
        }

        // ===== 입력값 검증 =====

        // qty(주문 수량) 검증
        // - 너의 기존 정책(0 초과, 최소단위, 소수 허용 여부 등)을 그대로 적용
        qty = validateStockQty(qty);

        // ===== 거래에 필요한 기본 정보 준비 =====

        // chart lock 다음에 account lock을 획득해 동일 계좌의 잔고 변경을 직렬화한다.
        PaperAccount acc = getAccountForUpdate(chart);

        // 이번 거래 대상 종목 ID 가져오기 (차트에 연결된 종목)
        Long symbolId = chart.getSymbol().getId();

        // 현재 캔들 가져오기
        TrainingSessionCandle currentCandle = getCurrentCandle(chart);
        // 현재 캔들 데이터에서 가격만 뽑고 BigDecimal로 변환
        BigDecimal price = BigDecimal.valueOf(currentCandle.getC());

        // 총 매수 금액 = 현재가 * 매수수량
        BigDecimal cost = price.multiply(qty);

        // ===== 잔고 검증 =====

        // 계좌 현금이 총 매수 금액보다 적으면 매수 불가
        if (acc.getCashBalance().compareTo(cost) < 0) {
            // 현금 부족 에러
            throw new CustomException(ErrorCode.INSUFFICIENT_CASH);
        }

        // ===== 포지션 처리(핵심) =====

        // 포지션은 "계좌+종목" 단일 유지 (멀티차트 1계좌 공유 핵심)
        // - 같은 계좌에서 같은 종목은 포지션을 1개만 유지하고 수량/평단만 갱신
        PaperPosition pos = positionRepo.findByAccountIdAndSymbolId(acc.getId(), symbolId).orElse(null);

        // 포지션이 없으면 신규 생성(첫 매수)
        if (pos == null) {
            pos = PaperPosition.builder()
                    // 포지션이 속한 계좌 세팅
                    .account(acc)
                    // 포지션 종목 ID 세팅
                    .symbolId(symbolId)
                    // 매수 수량 그대로 포지션 수량으로
                    .quantity(qty)
                    // 첫 매수라 평단=현재 체결가
                    .avgPrice(price)
                    .build();
        } else {
            // 포지션이 있으면 수량/평단을 "가중평균"으로 갱신(추가 매수)

            // 기존 보유 수량
            BigDecimal oldQty = pos.getQuantity();
            // 기존 평균 단가(평단)
            BigDecimal oldAvg = pos.getAvgPrice();
            // 새로운 총 수량 = 기존 수량 + 이번 매수 수량
            BigDecimal newQty = oldQty.add(qty);

            // 새로운 평균 단가(가중평균)
            // newAvg = (oldAvg*oldQty + price*qty) / newQty
            // - 소수점 4자리, 반올림(HALF_UP)로 계산(정책)
            BigDecimal newAvg = oldAvg.multiply(oldQty)
                    .add(price.multiply(qty))
                    .divide(newQty, 4, RoundingMode.HALF_UP);

            // 포지션 수량 갱신
            pos.setQuantity(newQty);
            // 포지션 평단 갱신
            pos.setAvgPrice(newAvg);
        }

        // ===== 계좌 잔고 갱신 =====

        // 매수했으니 계좌 현금에서 총 매수 금액 차감
        acc.setCashBalance(acc.getCashBalance().subtract(cost));

        // ===== DB 저장(정합성 반영) =====

        // 포지션 저장(신규 생성 또는 업데이트 반영)
        positionRepo.save(pos);
        // 계좌 저장(현금 차감 반영)
        accountRepo.save(acc);

        // ===== 거래 로그 기록(훈련 트레이드) =====

        // TradeNova 훈련 거래(로그) 저장
        Long riskRuleHistoryId = findLatestRiskHistoryId(chart.getId());
        TrainingTrade trade = tradeRepo.save(
                TrainingTrade.builder()
                        // 어느 차트에서 발생한 거래인지(차트 단위 로그)
                        .chartId(chart.getId())
                        // 어떤 계좌에서 거래했는지(계좌 단위 정산/조회용)
                        .accountId(acc.getId())
                        // 어떤 종목인지
                        .symbolId(symbolId)
                        .riskRuleHistoryId(riskRuleHistoryId)
                        // 매수/매도 구분 (여긴 BUY)
                        .side(TradeSide.BUY)
                        // 체결 가격
                        .price(price)
                        // 체결 수량
                        .qty(qty)
                        // 어떤 캔들에서 발생한 거래인지 저장
                        .candleTime(currentCandle.getT())
                        .build()
        );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("tradeId", trade.getId());
        payload.putPOJO("side", "BUY");
        payload.putPOJO("qty", qty);
        payload.putPOJO("executedPrice", price);
        payload.putPOJO("cashBalance", acc.getCashBalance());
        payload.putPOJO("positionQty", pos.getQuantity());
        payload.putPOJO("avgPrice", pos.getAvgPrice());
        putRiskRuleHistoryId(payload, trade.getRiskRuleHistoryId());

        eventService.append(
                userId,
                chart.getId(),
                Type.TRADE,
                chart.getSymbol().getName() + " " + qty + "주 매수",
                payload
        );

        // ===== 응답 DTO 구성 =====

        // 프론트에서 즉시 상태 반영할 수 있도록 스냅샷 형태로 응답 구성
        return new TradeResponse(
                // chartId: 어떤 차트에서 발생했는지
                chart.getId(),
                // tradeId: 저장된 거래 로그 ID
                trade.getId(),
                // cashBalance: 매수 후 남은 현금
                acc.getCashBalance(),
                // positionQty: 매수 후 보유 수량
                pos.getQuantity(),
                // avgPrice: 매수 후 평균 단가
                pos.getAvgPrice(),
                // executedPrice: 이번 거래 체결 가격(현재가)
                price,
                // candleTime: 거래가 발생한 캔들 시간
                currentCandle.getT()
        );
    }

    /**
     * 매도(SELL)
     * - qty만큼 부분 매도 지원
     */
    @Transactional
    public TradeResponse sell(Long userId, Long chartId, BigDecimal qty, boolean sellAll) {

        // chart lock을 먼저 획득해 같은 chart의 거래와 NEXT/ADVANCE를 직렬화한다.
        TrainingSessionChart chart = chartRepo.findForUpdateByIdAndUserId(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        return sellLocked(userId, chart, qty, sellAll);
    }

    /**
     * chart row lock이 이미 획득된 트랜잭션에서 매도를 실행한다.
     * sellAll이 public sell을 self-invocation하며 chart를 다시 조회하지 않도록 공통 로직을 분리했다.
     */
    private TradeResponse sellLocked(
            Long userId,
            TrainingSessionChart chart,
            BigDecimal qty,
            boolean sellAll
    ) {

        // 세션이 진행 중이 아니면 매도 금지 (종료 세션 조작 방지)
        if (chart.getSession().getStatus() != TrainingStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        // 차트가 COMPLETED 상태면 에러
        if (chart.getStatus() == TrainingChartStatus.COMPLETED) {
            throw new CustomException(ErrorCode.TRAINING_CHART_ALREADY_COMPLETED);
        }

        if (!sellAll) {
            qty = validateStockQty(qty);
        }

        // chart lock 다음에 account lock을 획득한다.
        PaperAccount acc = getAccountForUpdate(chart);
        // 차트에 연결된 종목 ID 가져오기
        Long symbolId = chart.getSymbol().getId();

        // 계좌+종목 기준으로 현재 포지션 조회
        // - 없으면 팔 게 없으므로 "보유 수량 부족" 에러로 처리
        PaperPosition pos = positionRepo.findByAccountIdAndSymbolId(acc.getId(), symbolId).orElse(null);

        if (pos == null || pos.getQuantity() == null ||
                pos.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            if (sellAll) {
                TrainingSessionCandle currentCandle = getCurrentCandle(chart);
                BigDecimal price = BigDecimal.valueOf(currentCandle.getC());
                return new TradeResponse(
                        chart.getId(),
                        null,
                        acc.getCashBalance(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        price,
                        currentCandle.getT()
                );
            }
            throw new CustomException(ErrorCode.INSUFFICIENT_POSITION_QTY);
        }

        // SELL ALL은 account lock 획득 후 조회한 최신 포지션 전체를 매도한다.
        qty = sellAll ? pos.getQuantity() : qty;

        // 보유 수량 < 매도 수량이면 매도 불가
        if (pos.getQuantity().compareTo(qty) < 0) {
            throw new CustomException(ErrorCode.INSUFFICIENT_POSITION_QTY);
        }

        // 현재 캔들 가져오기
        TrainingSessionCandle currentCandle = getCurrentCandle(chart);
        // 현재 캔들 데이터에서 가격만 뽑고 BigDecimal로 변환
        BigDecimal price = BigDecimal.valueOf(currentCandle.getC());

        // 매도 대금 = 체결가 * 매도 수량
        BigDecimal proceeds = price.multiply(qty);

        // ===== 계좌 현금 증가 =====

        // 매도했으니 현금 잔고 증가
        acc.setCashBalance(acc.getCashBalance().add(proceeds));

        // ===== 포지션 수량 차감 =====

        // 남은 수량 = 기존 보유 수량 - 매도 수량
        BigDecimal remain = pos.getQuantity().subtract(qty);

        // 남은 수량이 0이면 포지션 자체를 삭제(정리)
        if (remain.compareTo(BigDecimal.ZERO) == 0) {
            positionRepo.delete(pos);
        } else {
            // 남은 수량이 있으면 quantity만 갱신
            // (평단 avgPrice는 유지: 일반적인 포지션 모델)
            pos.setQuantity(remain);
            positionRepo.save(pos);
        }

        // 계좌 저장(현금 증가 반영)
        accountRepo.save(acc);

        // ===== 트레이드 로그 저장 =====

        Long riskRuleHistoryId = findLatestRiskHistoryId(chart.getId());
        TrainingTrade trade = tradeRepo.save(
                TrainingTrade.builder()
                        // 어느 차트에서 발생한 거래인지
                        .chartId(chart.getId())
                        // 어떤 계좌에서 발생한 거래인지
                        .accountId(acc.getId())
                        // 어떤 종목인지
                        .symbolId(symbolId)
                        .riskRuleHistoryId(riskRuleHistoryId)
                        // 매도
                        .side(TradeSide.SELL)
                        // 체결가
                        .price(price)
                        // 체결 수량
                        .qty(qty)
                        // 어느 캔들에서 발생한 거래인지 저장
                        .candleTime(currentCandle.getT())
                        .build()
        );



        // ===== 응답 스냅샷 구성 =====
        // 포지션이 0이 되면(삭제됨) 응답에서는 0,0으로 내려줘야 프론트가 깔끔하게 초기화 가능
        BigDecimal outQty = (remain.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : remain;
        BigDecimal outAvg = (remain.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : pos.getAvgPrice();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.putPOJO("tradeId", trade.getId());
        payload.put("side", "SELL");
        payload.put("sellAll", sellAll);
        payload.putPOJO("qty", qty);
        payload.putPOJO("executedPrice", price);
        payload.putPOJO("cashBalance", acc.getCashBalance());
        payload.putPOJO("positionQty", outQty);
        payload.putPOJO("avgPrice", outAvg);
        putRiskRuleHistoryId(payload, trade.getRiskRuleHistoryId());

        String summary = sellAll
                ? chart.getSymbol().getName() + " " + qty + "주 전량 매도"
                : chart.getSymbol().getName() + " " + qty + "주 매도";

        eventService.append(
                userId,
                chart.getId(),
                Type.TRADE,
                summary,
                payload
        );

        // 프론트 즉시 반영용 스냅샷 응답
        return new TradeResponse(
                // chartId
                chart.getId(),
                // tradeId
                trade.getId(),
                // 매도 후 현금 잔고
                acc.getCashBalance(),
                // 매도 후 보유 수량(0이면 0)
                outQty,
                // 매도 후 평단(0이면 0)
                outAvg,
                // 이번 매도 체결가
                price,
                // candleTime: 거래가 발생한 캔들 시간
                currentCandle.getT()
        );
    }


    /**
     * 자동청산 전용 전량매도
     *
     * 일반 sellAll과 다르게
     * - 자동청산 판정에서 결정한 체결가를 그대로 사용
     * - 자동청산이 발생한 candleTime을 그대로 저장
     */
    @Transactional
    public TradeResponse sellAllAtPrice(
            Long userId,
            Long chartId,
            BigDecimal executedPrice,
            Long candleTime,
            AutoExitReason reason
    ) {

        // 1. 차트 조회 + 소유권 검증
        TrainingSessionChart chart =
                chartRepo.findForUpdateByIdAndUserId(chartId, userId)
                        .orElseThrow(() ->
                                new CustomException(
                                        ErrorCode.TRAINING_CHART_NOT_FOUND
                                )
                        );

        return sellAllAtPriceLocked(
                userId,
                chart,
                executedPrice,
                candleTime,
                reason
        );
    }

    /**
     * chart row lock을 이미 획득한 트랜잭션에서 지정 가격으로 전량 청산한다.
     * advance의 자동청산과 마지막 봉 강제청산이 chart를 다시 조회하지 않고 같은 lock을 사용하게 한다.
     */
    TradeResponse sellAllAtPriceLocked(
            Long userId,
            TrainingSessionChart chart,
            BigDecimal executedPrice,
            Long candleTime,
            AutoExitReason reason
    ) {
        return sellAllAtPriceLockedResult(
                userId, chart, executedPrice, candleTime, reason
        ).response();
    }

    LockedSellResult sellAllAtPriceLockedResult(
            Long userId,
            TrainingSessionChart chart,
            BigDecimal executedPrice,
            Long candleTime,
            AutoExitReason reason
    ) {
        // 2. 세션 상태 검증
        if (chart.getSession().getStatus() != TrainingStatus.IN_PROGRESS) {
            throw new CustomException(
                    ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS
            );
        }

        // 3. 차트 상태 검증
        if (chart.getStatus() == TrainingChartStatus.COMPLETED) {
            throw new CustomException(
                    ErrorCode.TRAINING_CHART_ALREADY_COMPLETED
            );
        }

        // 4. chart lock 다음에 account lock을 획득한 계좌 / 종목 조회
        PaperAccount acc = getAccountForUpdate(chart);
        Long symbolId = chart.getSymbol().getId();

        // 5. 현재 포지션 조회
        PaperPosition pos =
                positionRepo
                        .findByAccountIdAndSymbolId(
                                acc.getId(),
                                symbolId
                        )
                        .orElse(null);

        // 포지션이 없으면 실제 거래는 만들지 않음
        if (pos == null ||
                pos.getQuantity() == null ||
                pos.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {

            return new LockedSellResult(
                    new TradeResponse(
                            chart.getId(),
                            null,
                            acc.getCashBalance(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            executedPrice,
                            candleTime
                    ),
                    BigDecimal.ZERO
            );
        }

        // 6. 전량매도 수량
        BigDecimal qty = pos.getQuantity();

        // 7. 매도대금 계산
        BigDecimal proceeds =
                executedPrice.multiply(qty);

        // 8. 현금 잔고 증가
        acc.setCashBalance(
                acc.getCashBalance().add(proceeds)
        );

        // 9. 전량청산이므로 포지션 삭제
        positionRepo.delete(pos);

        // 10. 계좌 저장
        accountRepo.save(acc);

        // 11. 거래 기록 저장
        Long riskRuleHistoryId = findLatestRiskHistoryId(chart.getId());
        TrainingTrade trade =
                tradeRepo.save(
                        TrainingTrade.builder()
                                .chartId(chart.getId())
                                .accountId(acc.getId())
                                .symbolId(symbolId)
                                .riskRuleHistoryId(riskRuleHistoryId)
                                .side(TradeSide.SELL)
                                .price(executedPrice)
                                .qty(qty)
                                .candleTime(candleTime)
                                .build()
                );

        // 12. TRADE 이벤트 저장
        ObjectNode payload =
                objectMapper.createObjectNode();

        payload.put("side", "SELL");
        payload.put("sellAll", true);
        payload.put("autoExit", true);

        payload.put(
                "autoExitReason",
                reason == null
                        ? "UNKNOWN"
                        : reason.name()
        );

        payload.putPOJO("tradeId", trade.getId());
        payload.putPOJO("qty", qty);
        payload.putPOJO("executedPrice", executedPrice);
        payload.putPOJO("candleTime", candleTime);
        payload.putPOJO("cashBalance", acc.getCashBalance());
        payload.putPOJO("positionQty", BigDecimal.ZERO);
        payload.putPOJO("avgPrice", BigDecimal.ZERO);
        putRiskRuleHistoryId(payload, trade.getRiskRuleHistoryId());

        String reasonName =
                reason == null
                        ? "UNKNOWN"
                        : reason.name();

        eventService.append(
                userId,
                chart.getId(),
                Type.TRADE,
                chart.getSymbol().getName()
                        + " "
                        + qty
                        + "주 자동청산 · "
                        + reasonName,
                payload
        );

        // 13. 프론트 응답
        return new LockedSellResult(
                new TradeResponse(
                        chart.getId(),
                        trade.getId(),
                        acc.getCashBalance(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        executedPrice,
                        candleTime
                ),
                qty
        );
    }

    record LockedSellResult(TradeResponse response, BigDecimal executedQty) {
    }

    /**
     * 자동매도/강제종료용: 전량 매도
     * - chart 기반으로 동작
     * - 보유 포지션이 없으면 "스냅샷만" 반환 (거래 로그 미생성)
     */
    @Transactional
    public TradeResponse sellAll(Long userId, Long chartId) {

        // chartId + userId 조건으로 차트 조회(소유권 검증 포함)
        TrainingSessionChart chart = chartRepo.findForUpdateByIdAndUserId(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // 세션이 진행 중이 아니면 거래 금지
        if (chart.getSession().getStatus() != TrainingStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        // 차트가 COMPLETED 상태면 에러
        if (chart.getStatus() == TrainingChartStatus.COMPLETED) {
            throw new CustomException(ErrorCode.TRAINING_CHART_ALREADY_COMPLETED);
        }

        return sellLocked(userId, chart, null, true);
    }

    /**
     * 특정 차트의 거래 내역 조회
     *
     * 역할:
     * - 해당 차트에서 발생한 모든 매수/매도 거래 조회
     * - 거래 발생 순서대로 반환
     *
     * 사용 위치:
     * - 거래 내역 패널
     * - BUY/SELL 마커 복원
     * - 세션 거래 로그
     */
    @Transactional
    public List<TrainingTradeItemResponse> getTrades(Long userId, Long chartId) {

        // 1) 차트 존재 여부 + 사용자 소유권 검증
        // - 해당 chartId가 현재 userId의 세션에 속한 차트인지 확인
        TrainingSessionChart chart = chartRepo.findByIdAndSession_User_Id(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // 2) 해당 차트의 거래 목록 조회 (오래된 거래 -> 최신 거래 순)
        return tradeRepo.findAllByChartIdOrderByIdAsc(chart.getId())
                .stream() // 3) List<TrainingTrade> -> Stream 변환)
                .map(trade -> new TrainingTradeItemResponse( // 4) 엔티티를 응답 DTO로 변환
                        trade.getId(),          // 거래 ID
                        trade.getChartId(),     // 거래가 발생한 차트 ID
                        trade.getAccountId(),   // 거래 계좌 ID
                        trade.getSymbolId(),    // 거래 종목 ID
                        trade.getSide(),        // 거래 방향 (BUY/SELL)
                        trade.getPrice(),       // 거래 체결 가격
                        trade.getQty(),          // 거래 수량
                        trade.getCandleTime(),   // 거래가 발생한 캔들 시간
                        trade.getCreatedAt()     // 거래 발생 시간
                ))
                // 5) Stream -> List 변환 후 반환
                .toList();
    }


    // ======================
    // helpers
    // ======================

    /**
     * 현재 progressIndex 위치의 캔들을 조회한다.
     *
     * 역할:
     * - 현재 차트 진행 위치(progressIndex)에 해당하는 캔들 반환
     * - 매수/매도 가격 계산
     * - candleTime 저장
     * - 현재 차트 상태 분석
     * 등에 사용된다.
     *
     * 흐름:
     * 1. progressIndex 조회
     * 2. 범위 보정 (0 ~ 마지막 idx)
     * 3. 해당 idx의 캔들 조회
     * 4. 없으면 예외 발생
     */
    private TrainingSessionCandle getCurrentCandle(TrainingSessionChart chart) {
        //현재 진행 위치(progressIndex) 조회
        //null이면 기본값 0 사용
        int idx = (chart.getProgressIndex() == null) ? 0 : chart.getProgressIndex();
        // 차트의 마지막 유효 idx 계산
        // bars가 100이면 마지막 idx는 99
        int maxIdx = Math.max(0, chart.getBars() - 1);

        // idx 범위 보정
        // - 음수 방지
        // - 마지막 idx 초과 방지
        idx = Math.max(0, Math.min(idx, maxIdx));

        // chartId + idx 기준 현재 캔들 조회
        return candleRepo.findByChartIdAndIdx(chart.getId(), idx)
                // 캔들이 없으면 예외 발생
                .orElseThrow(() -> new CustomException(ErrorCode.CANDLES_EMPTY));
    }

//    private BigDecimal getCurrentPrice(TrainingSessionChart chart) {
//        // progressIndex가 null이면 0으로 시작
//        int idx = (chart.getProgressIndex() == null) ? 0 : chart.getProgressIndex();
//        // bars-1이 최대 인덱스 (0 ~ bars-1)
//        int maxIdx = Math.max(0, chart.getBars() - 1);
//        // idx가 범위를 벗어나지 않도록 clamp 처리
//        idx = Math.max(0, Math.min(idx, maxIdx));
//
//        // (chartId, idx)로 특정 봉 1개 조회
//        TrainingSessionCandle candle = candleRepo.findByChartIdAndIdx(chart.getId(), idx)
//                .orElseThrow(() -> new CustomException(ErrorCode.CANDLES_EMPTY));
//
//        // candle의 종가(c)를 체결가/현재가로 사용
//        return BigDecimal.valueOf(candle.getC());
//    }

    private PaperAccount getAccountForUpdate(TrainingSessionChart chart) {
        Long accountId = chart.getSession().getAccount().getId();
        return accountRepo.findForUpdateById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAPER_ACCOUNT_NOT_FOUND));
    }

    private Long findLatestRiskHistoryId(Long chartId) {
        return riskHistoryRepo.findTopByChartIdOrderByIdDesc(chartId)
                .map(TrainingRiskRuleHistory::getId)
                .orElse(null);
    }

    private BigDecimal validateStockQty(BigDecimal qty) {
        // null이거나 0 이하이면 invalid
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE_QTY);
        }

        // 소수점 6자리까지 "버림"으로 정규화
        // - 예: 1.9999999 -> 1.999999
        qty = qty.setScale(6, RoundingMode.DOWN);

        // 버림 후 0이 되어버리면 invalid (예: 0.0000004 -> 0.000000)
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE_QTY);
        }

        // 주식 UX 정책: 소수점 금지
        // - stripTrailingZeros().scale() > 0 이면 소수점이 있다는 뜻
        // - 예: 1.0 -> scale 0, 1.5 -> scale 1
        if (qty.stripTrailingZeros().scale() > 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE_QTY);
        }

        // 검증/정규화된 qty 반환
        return qty;
    }

    private void putRiskRuleHistoryId(
            ObjectNode payload,
            Long riskRuleHistoryId
    ) {
        if (riskRuleHistoryId == null) {
            payload.putNull("riskRuleHistoryId");
        } else {
            payload.put("riskRuleHistoryId", riskRuleHistoryId);
        }
    }
}
