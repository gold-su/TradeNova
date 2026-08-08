package com.tradenova.training.service;

import com.tradenova.training.dto.AutoExitReason;
import com.tradenova.training.entity.TrainingRiskRule;
import com.tradenova.training.entity.TrainingSessionCandle;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor // final 필드 자동 생성자
public class TrainingAutoExitService {

    // TrainingRiskRule 데이터를 DB에서 조회하기 위한 Repository.
    // final이므로 객체 생성 이후 다른 Repository로 변경할 수 없다.
    // @RequiredArgsConstructor 덕분에 Spring이 생성자를 통해 자동으로 주입한다.
    private final TrainingRiskRuleRepository riskRepo;

    // record:
    // 데이터를 담아 전달하기 위한 간단한 클래스를 만들 때 사용하는 Java 문법.
    //
    // 일반 클래스로 만들었다면
    // private final 필드, 생성자, getter 등을 직접 작성해야 하지만
    // record를 사용하면 Java가 자동으로 만들어준다.
    //
    // 즉, 자동청산 검사 결과를 하나의 객체로 묶어서 반환하기 위한 DTO와 비슷한 역할이다.
    public record AutoExitResult(
            boolean autoExited,        //자동 청산 여부
            AutoExitReason reason,     //
            BigDecimal currentPrice,
            BigDecimal executedPrice
    ) {}

    /**
     * 특정 봉에서 자동청산 조건을 검사한다.
     *
     * 정책:
     * - 손절: low <= stopLoss
     * - 익절: high >= takeProfit
     * - 한 봉에서 둘 다 충족하면 손절 우선
     * - 손절가 아래로 갭 하락: open 체결
     * - 익절가 위로 갭 상승: open 체결
     * - 그 외에는 설정한 손절/익절 가격에 체결
     */

    // 이 메서드를 하나의 읽기 전용 트랜잭션 안에서 실행한다.
    //
    // readOnly = true:
    // 이 메서드는 DB 데이터를 조회만 하고 수정하지 않는다는 의미다.
    // 여기서는 riskRepo.findByChartId()로 리스크 룰만 조회하므로 readOnly가 적절하다.
    @Transactional(readOnly = true)
    public AutoExitResult checkAndAutoExit(
            Long chartId,                   // 자동청산 차트 ID
            TrainingSessionCandle candle    // 현재 NEXT로 새롭게 진행된 봉 데이터
    ) {

        // candle.getO()는 현재 봉의 시가(Open)를 가져온다.
        //
        // BigDecimal.valueOf(...):
        // 숫자를 BigDecimal 객체로 변환하는 메서드다.
        //
        // 예를 들어 candle.getO()가 100.5라면
        // BigDecimal.valueOf(100.5)를 통해
        // 가격 계산/비교에 사용할 BigDecimal 객체로 만든다.
        //
        // 돈이나 가격처럼 정확한 숫자 계산이 중요한 경우
        // double보다 BigDecimal을 사용하는 것이 안전하다.
        BigDecimal open = BigDecimal.valueOf(candle.getO());
        // 현재 봉의 고가(High)를 BigDecimal로 변환한다.
        BigDecimal high = BigDecimal.valueOf(candle.getH());
        // 현재 봉의 저가(Low)를 BigDecimal로 변환한다.
        BigDecimal low = BigDecimal.valueOf(candle.getL());
        // 현재 봉의 종가(Close)를 BigDecimal로 변환한다.
        BigDecimal close = BigDecimal.valueOf(candle.getC());

        // chartId에 해당하는 리스크 룰을 DB에서 조회한다.
        //
        // findByChartId(chartId)의 반환값은 Optional<TrainingRiskRule>이다.
        //
        // Optional은 "값이 있을 수도 있고 없을 수도 있음"을 표현하는 객체다.
        //
        // .orElse(null):
        // Optional 안에 값이 있으면 그 값을 반환하고,
        // 값이 없으면 null을 반환한다.
        //
        // 따라서 최종적으로:
        // 리스크 룰이 존재 -> rule에 TrainingRiskRule 객체 저장
        // 리스크 룰이 없음 -> rule에 null 저장
        TrainingRiskRule rule = riskRepo.findByChartId(chartId)
                .orElse(null);

        // 리스크 룰이 존재하지 않거나,
        // 리스크 룰의 자동청산 기능이 비활성화되어 있다면
        // 자동청산 검사를 할 필요가 없다.
        //
        // || 는 OR(또는) 연산자다.
        // 둘 중 하나라도 true이면 전체 조건이 true가 된다.
        //
        // ! 는 boolean 값을 반대로 뒤집는다.
        // rule.isEnabled() == true라면
        // !rule.isEnabled() == false가 된다.
        if (rule == null || !rule.isEnabled()) {

            // 자동청산이 발생하지 않았다는 결과를 반환한다.
            //
            // false = 자동청산 안 됨
            // null = 자동청산 사유 없음
            // close = 현재 가격은 현재 봉의 종가
            // null = 실제 청산 체결 가격 없음
            return new AutoExitResult(
                    false,
                    null,
                    close,
                    null
            );
        }

        // 손절 조건을 익절 조건보다 먼저 검사한다.
        //
        // 한 봉 안에서 손절가와 익절가를 모두 터치할 수도 있는데,
        // OHLC 데이터만 가지고는 실제로 어느 가격을 먼저 터치했는지 알 수 없다.
        // 따라서 보수적으로 손절이 먼저 발생했다고 처리한다.
        if (
                // 손절 가격이 설정되어 있는지 확인한다. NULL이면 사용자가 손절가를 설정하지 않았다는 뜻.
                rule.getStopLossPrice() != null &&
                        // 현재 봉의 저가(low)가 손절가보다 작거나 같은지 검사한다.
                        //
                        // BigDecimal은 <, > 같은 연산자로 직접 크기 비교를 하지 않고
                        // compareTo()를 사용한다.
                        //
                        // A.compareTo(B)의 결과:
                        // 음수 -> A < B
                        // 0   -> A == B
                        // 양수 -> A > B
                        //
                        // 따라서
                        // low.compareTo(stopLossPrice) <= 0
                        //
                        // 는
                        // low <= stopLossPrice
                        //
                        // 라는 의미다.
                low.compareTo(rule.getStopLossPrice()) <= 0) {
            // 실제로 어느 가격에 손절 체결할지를 결정한다.
            BigDecimal executedPrice =
                    // 현재 봉의 시가(open)가 손절가보다 작거나 같은지 검사한다.
                    //
                    // 즉 이미 손절가 아래에서 장이 시작된
                    // "갭 하락" 상황인지 확인한다.
                    open.compareTo(rule.getStopLossPrice()) <= 0
                            // ? : 는 삼항 연산자다.
                            //
                            // 조건 ? 참일 때 값 : 거짓일 때 값
                            //
                            // 여기서는 open <= stopLossPrice라면
                            // 이미 손절가보다 낮은 가격에서 봉이 시작했으므로
                            // 손절가가 아니라 실제 시가(open)에 체결된 것으로 처리한다.
                            ? open
                            // 시가가 손절가보다 위에 있었다면
                            // 봉이 진행되는 도중 손절가까지 하락했다고 판단한다.
                            //
                            // 따라서 사용자가 설정한 손절가에 체결한다.
                            : rule.getStopLossPrice();

            // 손절 자동청산 결과를 즉시 반환한다.
            //
            // 여기서 return되므로 아래의 익절 검사 코드는 실행되지 않는다.
            return new AutoExitResult(
                    true,            // 자동청산 발생
                    AutoExitReason.STOP_LOSS, // 자동청산 이유는 손절
                    close,                    // NEXT 이후 현재 가격은 해당 봉의 종가
                    executedPrice             // 실제 손절 체결 가격
            );
        }


        // 익절 조건을 검사한다.
        if (
            // 사용자가 익절 가격을 설정했는지 확인한다.
                rule.getTakeProfitPrice() != null &&

                        // 현재 봉의 고가(high)가 익절가 이상인지 검사한다.
                        //
                        // compareTo 결과가 0 이상이라는 것은
                        // high >= takeProfitPrice라는 뜻이다.
                        //
                        // 즉 현재 봉이 진행되는 동안 익절 가격에 도달했는지 확인한다.
                        high.compareTo(rule.getTakeProfitPrice()) >= 0
        ) {

            // 실제 익절 체결 가격을 결정한다.
            BigDecimal executedPrice =

                    // 현재 봉의 시가가 이미 익절가 이상인지 확인한다.
                    //
                    // open >= takeProfitPrice
                    //
                    // 라면 익절가 위에서 갭 상승하여 봉이 시작된 것이다.
                    open.compareTo(rule.getTakeProfitPrice()) >= 0

                            // 갭 상승한 경우 실제 시가(open)에 체결된 것으로 처리한다.
                            ? open

                            // 일반적인 경우 봉 진행 중 익절가에 도달했다고 보고
                            // 설정한 익절 가격에 체결한다.
                            : rule.getTakeProfitPrice();


            // 익절 자동청산 결과를 반환한다.
            return new AutoExitResult(

                    // 자동청산 발생
                    true,

                    // 자동청산 사유는 익절
                    AutoExitReason.TAKE_PROFIT,

                    // NEXT 이후 현재 가격은 현재 봉의 종가
                    close,

                    // 실제 익절 체결 가격
                    executedPrice
            );
        }


        // 여기까지 왔다는 것은
        //
        // 1. 리스크 룰은 활성화되어 있지만
        // 2. 손절 가격에는 도달하지 않았고
        // 3. 익절 가격에도 도달하지 않았다는 뜻이다.
        //
        // 따라서 자동청산 없이 현재 포지션을 유지한다.
        return new AutoExitResult(

                // 자동청산 안 됨
                false,

                // 자동청산 사유 없음
                null,

                // 현재 가격은 현재 봉의 종가
                close,

                // 청산되지 않았으므로 체결 가격 없음
                null
        );
    }
}