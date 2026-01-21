package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.training.dto.AutoExitResult;
import com.tradenova.training.entity.TrainingRiskRule;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import com.tradenova.training.repository.TrainingSessionCandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TrainingAutoExitService {

    private final TrainingRiskRuleRepository riskRepo;
    private final TrainingSessionCandleRepository candleRepo;

    //한 번의 progress 진행에 대한 record
    public record AutoExitResult(
            boolean autoExited, // 자동청산
            String reason,      // "STOP_LOSS" | "TAKE_PROFIT" | null
            BigDecimal currentPrice // 현재 봉 close 가격
    ){}

    /**
     * progressIndex가 갱신된 직후 호출됨.
     * - currentPrice 계산
     * - enabled 룰이면 stop/take 조건 체크
     * - 지금 단계에선 "자동청산 발생 여부"만 반환 (다음 단계에서 실제 SELL 처리 붙임)
     */
    @Transactional(readOnly = true)
    public AutoExitResult checkAndAutoExit(Long userId, TrainingSession s) {

        // 현재가 계산
        BigDecimal currentPrice = getCurrentPriceFromDb(s);

        // 리스크 룰 조회
        TrainingRiskRule rule = riskRepo.findBySessionIdAndAccountId(s.getId(), s.getAccount().getId())
                .orElse(null);

        // 룰 없거나 비활성화면 종료
        if(rule == null || !rule.isEnabled()) {
            return new AutoExitResult(false, null, currentPrice);
        }

        // 우선순위: STOP_LOSS 먼저 (보수적)
        if (rule.getStopLossPrice() != null &&
                currentPrice.compareTo(rule.getStopLossPrice()) <= 0) {
            return new AutoExitResult(true, "STOP_LOSS", currentPrice);
        }

        // TakeProfit 판정
        if (rule.getTakeProfitPrice() != null &&
                currentPrice.compareTo(rule.getTakeProfitPrice()) >= 0) {
            return new AutoExitResult(true, "TAKE_PROFIT", currentPrice);
        }

        // 아무것도 아니면 false
        return new AutoExitResult(false, null, currentPrice);
    }

    // 세션의 progressIndex에 해당하는 캔들(close)을 DB에서 찾아 현재가로 사용한다.
    private BigDecimal getCurrentPriceFromDb(TrainingSession s) {

        int idx = (s.getProgressIndex() == null) ? 0 : s.getProgressIndex();

        TrainingSessionCandle candle = candleRepo.findBySessionIdAndIdx(s.getId(), idx)
                .orElseThrow(() -> new CustomException(ErrorCode.CANDLES_EMPTY));

        // candle.c 타입이 double이면 valueOf 사용
        return BigDecimal.valueOf(candle.getC());
    }
}
