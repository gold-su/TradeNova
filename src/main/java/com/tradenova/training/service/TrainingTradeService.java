package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.training.repository.TrainingTradeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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


    }

}
