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

    // 세션(훈련) 조회용
    private final TrainingSessionRepository sessionRepo;
    // 세션에 저장된 캔들 조회용 (현재가 계산에 사용)
    private final TrainingSessionCandleRepository candleRepo;
    // 훈련 매매 기록(TrainingTrader) 조회용
    private final TrainingTradeRepository tradeRepo;
    // 페이퍼 계좌/포지션 관련 (현금/보유수량 갱신)
    private final PaperAccountRepository accountRepo;  // 지금 코드에서는 직접 사용 안 함 (acc가 영속 상태라 dirty checking 기대)
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
    public TradeResponse buy(Long userId, Long sessionId, BigDecimal qty){

        // 1) 세션 조회 + 소유권 검증 (남의 세션이면 조회 자체가 안 됨)
        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
        
        // 1-1) qty 검증 (null / 0 / 음수 금지)
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE_QTY);
        }
        //  qty 스케일 정책 (MVP 기본)
        qty = qty.setScale(6, RoundingMode.DOWN);
        // 0 되면 차단
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE_QTY);
        }
        // 주식 UX: 소수점 허용 안 함
        if (qty.stripTrailingZeros().scale() > 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE_QTY);
        }
        // 1-2) COMPLETED 세션이면 거래 금지
        if (s.getStatus() != TrainingStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        // 2) 세션에 연결된 페이퍼 계좌 (JPA 영속 상태일 가능성 높음)
        PaperAccount acc = s.getAccount(); // 주석: 영속 상태라는 전제 → setCashBalance하면 dirty checking으로 update 가능

        // 3) 현재 세션의 종목 ID
        Long symbolId = s.getSymbol().getId();

        // 4) 현재가 계산: progressIndex 기준으로 candle.close 가져옴
        BigDecimal price = getCurrentPrice(s);

        // 5) 총 매수 비용 = 현재가 * 수량
        BigDecimal cost = price.multiply(qty);

        // 6) 현금 부족 체크: cashBalance < cost 이면 매수 불가
        if (acc.getCashBalance().compareTo(cost) < 0) {
            // 지금은 INVALID_REQUEST 사용 중 (나중에 CASH_NOT_ENOUGH 같은 에러 코드 분리하면 UX 좋아짐)
            throw new CustomException(ErrorCode.INSUFFICIENT_CASH);
        }

        // 7) 포지션 조회: (accountId + symbolId)로 단일 포지션을 유지하는 구조
        //    없으면 신규 생성, 있으면 추가매수로 평단 갱신
        PaperPosition pos = positionRepo.findByAccountIdAndSymbolId(acc.getId(), symbolId)
                .orElse(null);

        if (pos == null) {
            // 8-A) 포지션이 없으면 "첫 매수" → 신규 생성
            pos = PaperPosition.builder()
                    .account(acc)      // FK 연결
                    .symbolId(symbolId)
                    .quantity(qty)
                    .avgPrice(price)   // 첫 매수는 평단 = 현재가
                    .build();
        } else {
            // 8-B) 포지션이 있으면 "추가 매수" → 가중평균 평단 계산
            BigDecimal oldQty = pos.getQuantity(); // 기존 수량
            BigDecimal oldAvg = pos.getAvgPrice(); // 기존 평단
            BigDecimal newQty = oldQty.add(qty);   // 새 수량 = 기존 + 추가

            // 새 평단 = (기존평단*기존수량 + 현재가*추가수량) / 새수량
            // scale=4, HALF_UP 반올림 정책
            BigDecimal newAvg = oldAvg.multiply(oldQty)
                    .add(price.multiply(qty))
                    .divide(newQty, 4, RoundingMode.HALF_UP);

            // 포지션 상태 업데이트 (영속 상태면 dirty checking으로 반영됨)
            pos.setQuantity(newQty);
            pos.setAvgPrice(newAvg);
        }

        // 9) 현금 차감 (매수했으니 cashBalance 감소)
        acc.setCashBalance(acc.getCashBalance().subtract(cost));

        // 10) 포지션 저장
        // - pos가 신규면 save 필요
        // - 기존 pos라도 명시 save 해도 OK (dirty checking + save 모두 가능)
        positionRepo.save(pos);

        // 계좌 cashBalance 저장 확실히 반영
        accountRepo.save(acc);

        // 11) 훈련 매매 기록 저장 (BUY 체결 기록)
        TrainingTrade trade = tradeRepo.save(
                TrainingTrade.builder()
                        .sessionId(s.getId())     // 어떤 훈련 세션에서 발생했는지
                        .accountId(acc.getId())   // 어떤 페이퍼 계좌에서 발생했는지
                        .symbolId(symbolId)       // 종목
                        .side(TradeSide.BUY)      // 매수
                        .price(price)             // 체결가(현재가 close 기준)
                        .qty(qty)                 // 체결수량
                        .build()
        );

        // 12) 응답: 거래 후 계좌/포지션 스냅샷 + executedPrice(체결가)
        return new TradeResponse(
                s.getId(),
                trade.getId(),
                acc.getCashBalance(),
                pos.getQuantity(),
                pos.getAvgPrice(),
                price
        );
    }

    @Transactional
    public TradeResponse sell(Long userId, Long sessionId, BigDecimal qty) {

        // 1) 세션 조회 + 소유권 검증
        // - sessionId와 userId로 조회해서 남의 세션은 접근 불가
        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // qty 검증
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE_QTY);
        }
        //  qty 스케일 정책 (MVP 기본)
        qty = qty.setScale(6, RoundingMode.DOWN);
        // 0 되면 차단
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_TRADE_QTY);
        }
        // 주식 UX: 소수점 허용 안 함
        if (qty.stripTrailingZeros().scale() > 0) { // 소수점 존재
            throw new CustomException(ErrorCode.INVALID_TRADE_QTY);
        }

        if (s.getStatus() != TrainingStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
        }

        // 2) 이 세션이 사용하는 페이퍼 계좌(현금이 여기서 늘어남)
        PaperAccount acc = s.getAccount();

        // 3) 세션의 종목 ID (포지션 찾을 때 사용)
        Long symbolId = s.getSymbol().getId();

        // 4) 계좌 + 종목으로 현재 포지션 조회
        // - 너의 정책(실전 느낌)에서는 "포지션은 계좌 소유"라서 이게 핵심 구조임.
        PaperPosition pos = positionRepo.findByAccountIdAndSymbolId(acc.getId(), symbolId)
                .orElseThrow(() -> new CustomException(ErrorCode.INSUFFICIENT_POSITION_QTY)); // 보유 없음

        // 5) 보유 수량 체크: 보유량 < 매도요청량이면 불가능
        if (pos.getQuantity().compareTo(qty) < 0) {
            throw new CustomException(ErrorCode.INSUFFICIENT_POSITION_QTY); // 보유수량 부족
        }

        // 6) 현재가 계산(체결가): progressIndex 기준 candle.close
        BigDecimal price = getCurrentPrice(s);

        // 7) 매도대금 = 체결가 * 수량
        BigDecimal proceeds = price.multiply(qty);

        // 8) 현금 증가: 매도했으니 cashBalance += proceeds
        acc.setCashBalance(acc.getCashBalance().add(proceeds));

        // 9) 포지션 수량 감소: 남은 수량 = 기존 - 매도수량
        BigDecimal remain = pos.getQuantity().subtract(qty);

        if (remain.compareTo(BigDecimal.ZERO) == 0) {
            // 9-A) 전량 매도면 포지션 자체를 삭제(보유상태 제거)
            positionRepo.delete(pos);
        } else {
            // 9-B) 일부 매도면 quantity만 갱신 (평단은 그대로 유지되는 정책)
            pos.setQuantity(remain);

            // save는 명시적으로 호출(영속 상태면 dirty checking으로도 되지만 OK)
            positionRepo.save(pos);
        }

        accountRepo.save(acc);

        // 10) 훈련 거래 기록 저장(SELL)
        // - 이건 "현재 보유상태"가 아니라 "히스토리" 역할
        TrainingTrade trade = tradeRepo.save(
                TrainingTrade.builder()
                        .sessionId(s.getId())     // 어떤 훈련 세션에서 발생했는지(훈련 단위 로그)
                        .accountId(acc.getId())   // 어떤 계좌에서 발생했는지(계좌 단위 포트폴리오)
                        .symbolId(symbolId)       // 종목
                        .side(TradeSide.SELL)     // 매도
                        .price(price)             // 체결가
                        .qty(qty)                 // 체결수량
                        .build()
        );

        // 11) 응답용 qty/avg 세팅
        // - 전량 매도면 0으로 내려보냄
        BigDecimal outQty = (remain.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : remain;

        // - 전량 매도면 avgPrice도 0으로 내려보내고 있음 (정책적으로는 null이 더 명확할 수 있음)
        BigDecimal outAvg = (remain.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ZERO : pos.getAvgPrice();

        // 12) TradeResponse 반환: "거래 후 상태 스냅샷"
        return new TradeResponse(
                s.getId(),              // sessionId
                trade.getId(),          // tradeId
                acc.getCashBalance(),   // 거래 후 현금
                outQty,                 // 거래 후 보유 수량
                outAvg,                 // 거래 후 평단
                price                  // 이번 거래 체결가
        );
    }

    /**
     * 자동매도/강제종료용: 전량 매도
     */
    @Transactional
    public TradeResponse sellAll(Long userId, TrainingSession s) {

        // 1) 세션이 쓰는 계좌/종목
        PaperAccount acc = s.getAccount();
        Long symbolId = s.getSymbol().getId();

        // 2) 계좌+종목 기준 포지션 조회
        PaperPosition pos = positionRepo.findByAccountIdAndSymbolId(acc.getId(), symbolId)
                .orElse(null);

        // 3) 포지션이 없거나 수량이 0 이하면 팔 게 없음
        if (pos == null || pos.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            // 전량매도 할 게 없는 경우: tradeId는 null로 내려줌(실제 거래 기록 없음)
            // executedPrice는 현재가로 내려줘서 UI/로그에 쓸 수 있게 함
            return new TradeResponse(
                    s.getId(),
                    null,
                    acc.getCashBalance(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    getCurrentPrice(s)
            );
        }

        // 4) 전량매도는 결국 sell()로 위임 (단, sell()이 내부에서 세션을 다시 조회해서 약간 중복)
        return sell(userId, s.getId(), pos.getQuantity());
    }


    private BigDecimal getCurrentPrice(TrainingSession s) {
        // 1) null-safe: progressIndex가 null이면 0으로
        int idx = (s.getProgressIndex() == null) ? 0 : s.getProgressIndex();

        // 2) range-safe: 0 ~ (bars-1) 범위로 강제 보정
        int maxIdx = Math.max(0, s.getBars() - 1);
        idx = Math.max(0, Math.min(idx, maxIdx));

        // 3) sessionId + idx로 캔들 조회
        TrainingSessionCandle candle = candleRepo.findBySessionIdAndIdx(s.getId(), idx)
                .orElseThrow(() -> new CustomException(ErrorCode.CANDLES_EMPTY));

        // 4) close 가격 반환
        return BigDecimal.valueOf(candle.getC());
    }

}
