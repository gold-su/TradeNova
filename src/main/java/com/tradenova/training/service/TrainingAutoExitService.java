package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.training.dto.AutoExitReason;
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

    //한 번의 progress 진행에 대한 record / 서비스 내부 전용 반환 타입이라서 클래스 안에 코딩
    public record AutoExitResult(
            boolean autoExited, // 자동청산
            AutoExitReason reason,      // "STOP_LOSS" | "TAKE_PROFIT" | null
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
            return new AutoExitResult(true, AutoExitReason.STOP_LOSS, currentPrice);
        }

        // TakeProfit 판정
        if (rule.getTakeProfitPrice() != null &&
                currentPrice.compareTo(rule.getTakeProfitPrice()) >= 0) {
            return new AutoExitResult(true, AutoExitReason.TAKE_PROFIT, currentPrice);
        }

        // 아무것도 아니면 false
        return new AutoExitResult(false, null, currentPrice);
    }

    // 세션의 progressIndex에 해당하는 종가(close)을 DB에서 찾아 현재가로 사용한다.
    private BigDecimal getCurrentPriceFromDb(TrainingSession s) {
        // 1) progressIndex가 null이면 아직 진행값이 없다는 의미로 보고 0번 봉을 기본값으로 사용
        int idx = (s.getProgressIndex() == null) ? 0 : s.getProgressIndex();
        // 2) 세션 bars 기준으로 "마지막 유효 인덱스"를 계산
        //    - bars = 120이면 idx는 0~119가 유효
        //    - bars가 0이거나 이상값이어도 최소 0으로 방어
        int maxIdx = Math.max(0, s.getBars() - 1);

        // 3) idx를 유효 범위로 강제 보정(clamp)
        //    - 음수면 0으로 올리고
        //    - maxIdx보다 크면 maxIdx로 내림
        //    => DB 조회 시 존재하지 않는 idx를 찍어서 예외가 나는 걸 방지
        idx = Math.max(0, Math.min(idx, maxIdx));

        // 4) (sessionId, idx)로 세션 캔들 1개를 조회
        //    - 치팅 방지 핵심: "프론트가 보내는 가격"이 아니라 "세션에 저장된 캔들"로 현재가를 계산
        //    - 없으면 CANDLES_EMPTY 에러로 처리(세션 캔들이 비정상적으로 비어있거나 idx가 꼬인 케이스)
        TrainingSessionCandle candle = candleRepo.findBySessionIdAndIdx(s.getId(), idx)
                .orElseThrow(() -> new CustomException(ErrorCode.CANDLES_EMPTY));

        // 5) close 가격(candle.getC())을 BigDecimal로 변환해서 반환
        //    - 엔티티에서 c가 double이면 BigDecimal.valueOf(double)을 쓰는 게 new BigDecimal(double)보다 안전(표현 오차 완화)
        return BigDecimal.valueOf(candle.getC());
    }
}
