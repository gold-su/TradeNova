package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.entity.PaperPosition;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.TradeSide;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingTrade;
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
            throw new CustomException(ErrorCode.INVALID_REQUEST);
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

        // 11) 훈련 매매 기록 저장 (BUY 체결 기록)
        TrainingTrade trade = tradeRepo.save(
                TrainingTrade.builder()
                        .sessionId(s.getId())     // 어떤 훈련 세션에서 발생했는지
                        .accountId(acc.getId())   // 어떤 페이퍼 계좌에서 발생했는지
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
    public TradeResponse sell(Long userId, Long sessionId, Long tradeId, BigDecimal qty){
        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        PaperAccount acc = s.getAccount();
        Long symbolId = s.getSymbol().getId();
    }
}
