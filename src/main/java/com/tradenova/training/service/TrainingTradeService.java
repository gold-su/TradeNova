package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.entity.PaperPosition;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.training.dto.TradeResponse;
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

@Service
@RequiredArgsConstructor
public class TrainingTradeService {

    // chart 기반 조회
    private final TrainingSessionChartRepository chartRepo;
    // 세션에 저장된 캔들 조회용 (현재가 계산에 사용)
    private final TrainingSessionCandleRepository candleRepo;
    // 훈련 매매 기록(TrainingTrader) 조회용
    private final TrainingTradeRepository tradeRepo;
    // 페이퍼 계좌/포지션 관련 (현금/보유수량 갱신)
    private final PaperAccountRepository accountRepo;
    private final PaperPositionRepository positionRepo;

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
        TrainingSessionChart chart = chartRepo.findByIdAndSession_User_Id(chartId, userId)
                // 없으면 404 성격의 커스텀 예외(차트 없음 또는 남의 차트)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // ===== 세션 상태 검증 =====

        // 세션 정책: 세션이 진행중(IN_PROGRESS)이 아니면 차트 거래도 금지
        // - COMPLETED 등 종료 상태에서 매수/매도하면 정합성 깨짐
        if (chart.getSession().getStatus() != TrainingStatus.IN_PROGRESS) {
            // 세션이 진행 중이 아님 → 거래 불가 에러
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        // ===== 입력값 검증 =====

        // qty(주문 수량) 검증
        // - 너의 기존 정책(0 초과, 최소단위, 소수 허용 여부 등)을 그대로 적용
        qty = validateStockQty(qty);

        // ===== 거래에 필요한 기본 정보 준비 =====

        // 이 차트가 속한 세션의 모의투자 계좌(PaperAccount) 가져오기
        PaperAccount acc = chart.getSession().getAccount();

        // 이번 거래 대상 종목 ID 가져오기 (차트에 연결된 종목)
        Long symbolId = chart.getSymbol().getId();

        // 현재가 계산(차트의 progressIndex 기준으로 현재 봉의 가격 등)
        // - 매수 체결 가격으로 사용
        BigDecimal price = getCurrentPrice(chart);

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
        TrainingTrade trade = tradeRepo.save(
                TrainingTrade.builder()
                        // 어느 차트에서 발생한 거래인지(차트 단위 로그)
                        .chartId(chart.getId())
                        // 어떤 계좌에서 거래했는지(계좌 단위 정산/조회용)
                        .accountId(acc.getId())
                        // 어떤 종목인지
                        .symbolId(symbolId)
                        // 매수/매도 구분 (여긴 BUY)
                        .side(TradeSide.BUY)
                        // 체결 가격
                        .price(price)
                        // 체결 수량
                        .qty(qty)
                        .build()
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
                price
        );
    }

    /**
     * 매도(SELL)
     * - qty만큼 부분 매도 지원
     */
    @Transactional
    public TradeResponse sell(Long userId, Long chartId, BigDecimal qty) {

        // chartId + (chart.session.user.id == userId) 조건으로 차트 조회
        // - 남의 차트 접근을 구조적으로 차단 (없으면 404 성격)
        TrainingSessionChart chart = chartRepo.findByIdAndSession_User_Id(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // 세션이 진행 중이 아니면 매도 금지 (종료 세션 조작 방지)
        if (chart.getSession().getStatus() != TrainingStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        // 매도 수량 검증 (null/0 이하/소수점 금지 등 기존 정책 적용)
        qty = validateStockQty(qty);

        // 이 차트가 속한 세션의 계좌 가져오기
        PaperAccount acc = chart.getSession().getAccount();
        // 차트에 연결된 종목 ID 가져오기
        Long symbolId = chart.getSymbol().getId();

        // 계좌+종목 기준으로 현재 포지션 조회
        // - 없으면 팔 게 없으므로 "보유 수량 부족" 에러로 처리
        PaperPosition pos = positionRepo.findByAccountIdAndSymbolId(acc.getId(), symbolId)
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_POSITION_QTY));

        // 보유 수량 < 매도 수량이면 매도 불가
        if (pos.getQuantity().compareTo(qty) < 0) {
            throw new CustomException(ErrorCode.INSUFFICIENT_POSITION_QTY);
        }

        // 현재 진행 인덱스(progressIndex)의 캔들로부터 현재가 산출 (체결가)
        BigDecimal price = getCurrentPrice(chart);
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

        TrainingTrade trade = tradeRepo.save(
                TrainingTrade.builder()
                        // 어느 차트에서 발생한 거래인지
                        .chartId(chart.getId())
                        // 어떤 계좌에서 발생한 거래인지
                        .accountId(acc.getId())
                        // 어떤 종목인지
                        .symbolId(symbolId)
                        // 매도
                        .side(TradeSide.SELL)
                        // 체결가
                        .price(price)
                        // 체결 수량
                        .qty(qty)
                        .build()
        );

        // ===== 응답 스냅샷 구성 =====
        // 포지션이 0이 되면(삭제됨) 응답에서는 0,0으로 내려줘야 프론트가 깔끔하게 초기화 가능
        BigDecimal outQty = (remain.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : remain;
        BigDecimal outAvg = (remain.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : pos.getAvgPrice();

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
                price
        );
    }

    /**
     * 자동매도/강제종료용: 전량 매도
     * - chart 기반으로 동작
     * - 보유 포지션이 없으면 "스냅샷만" 반환 (거래 로그 미생성)
     */
    @Transactional
    public TradeResponse sellAll(Long userId, Long chartId) {

        // chartId + userId 조건으로 차트 조회(소유권 검증 포함)
        TrainingSessionChart chart = chartRepo.findByIdAndSession_User_Id(chartId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_CHART_NOT_FOUND));

        // 세션이 진행 중이 아니면 거래 금지
        if (chart.getSession().getStatus() != TrainingStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        // 세션 계좌 가져오기
        PaperAccount acc = chart.getSession().getAccount();

        // 종목 ID 가져오기
        Long symbolId = chart.getSymbol().getId();

        // 계좌+종목 포지션 조회 (없을 수도 있음)
        PaperPosition pos = positionRepo.findByAccountIdAndSymbolId(acc.getId(), symbolId).orElse(null);

        // 팔 게 없으면:
        // - trade 로그를 만들 필요 없음
        // - tradeId는 null로 내려주고
        // - 현재 스냅샷(잔고/포지션=0/현재가)만 반환
        if (pos == null || pos.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            return new TradeResponse(
                    chart.getId(),
                    null,           // tradeId 없음(거래 미발생)
                    acc.getCashBalance(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    getCurrentPrice(chart) // 현재가는 표시용으로 내려줌
            );
        }

        // 실제 전량 매도는 sell() 재사용
        // - 수량 = 현재 포지션 보유 수량
        return sell(userId, chart.getId(), pos.getQuantity());
    }

    // ======================
    // helpers
    // ======================

    private BigDecimal getCurrentPrice(TrainingSessionChart chart) {
        // progressIndex가 null이면 0으로 시작
        int idx = (chart.getProgressIndex() == null) ? 0 : chart.getProgressIndex();
        // bars-1이 최대 인덱스 (0 ~ bars-1)
        int maxIdx = Math.max(0, chart.getBars() - 1);
        // idx가 범위를 벗어나지 않도록 clamp 처리
        idx = Math.max(0, Math.min(idx, maxIdx));

        // (chartId, idx)로 특정 봉 1개 조회
        TrainingSessionCandle candle = candleRepo.findByChartIdAndIdx(chart.getId(), idx)
                .orElseThrow(() -> new CustomException(ErrorCode.CANDLES_EMPTY));

        // candle의 종가(c)를 체결가/현재가로 사용
        return BigDecimal.valueOf(candle.getC());
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

}
