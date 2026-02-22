package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.paper.entity.PaperPosition;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.training.dto.SessionProgressResponse;
import com.tradenova.training.dto.TradeResponse;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
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

        // 여기서 flush 한번 걸어주면 이후 로직에서 progressIndex 기준 조회가 안정적
        // (candle 조회/autoExit/거래 로직이 같은 트랜잭션에서 일관되게 동작)
        chartRepo.flush();

        // 6) chart가 마지막 봉까지 가면(옵션) 세션까지 종료할지?
        //    MVP에서는 "세션 종료는 세션 정책으로 따로" 가도 되는데,
        //    지금은 단순하게 chart가 끝까지 가면 세션도 끝내는 걸로 처리해도 됨.
        if (nextIdx >= maxIdx) {
            // chart 자체만 종료 상태 필드 추가하는게 이상적이지만
            // 지금은 세션 종료는 건드리지 않는게 안정적
        }

        // 7) 자동청산 체크 (반환: autoExited 여부 + reason + currentPrice)
        //    - stop loss / take profit 조건 충족 여부 판단
        //    - 지금 단계에서는 "실제 매도"가 아니라
        //    - autoExited 여부와 사유만 계산
        TrainingAutoExitService.AutoExitResult r =
                autoExitService.checkAndAutoExit(chart.getId(), chart);

        // 8) 현재가 (progressIndex 기준 close 가격)
        BigDecimal currentPrice = r.currentPrice();

        // 9) 스냅샷 구성
        Long accountId = chart.getSession().getAccount().getId();
        Long symbolId = chart.getSymbol().getId();

        PaperPosition pos = positionRepo.findByAccountIdAndSymbolId(accountId, symbolId)
                .orElse(null);

        BigDecimal positionQty = (pos == null) ? BigDecimal.ZERO : pos.getQuantity();
        BigDecimal avgPrice = (pos == null) ? BigDecimal.ZERO : pos.getAvgPrice();
        // 현금 (PaperAccount에 맞는 getter로 바꾸기)
        BigDecimal cashBalance = chart.getSession().getAccount().getCashBalance();

        // 10) 자동청산
        //     - 룰은 발동했는데 포지션이 0이면, 팔 게 없으니 자동청산 실행은 스킵(UX 깔끔)
        boolean executedAutoExit = false;
        var autoExitReason = r.reason();

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
        }

        // 11) 프론트로 내려줄 진행 결과 응답 DTO 생성
        return new SessionProgressResponse(
                chart.getId(),              // chartId
                chart.getProgressIndex(),   // 현재 진행 인덱스
                currentPrice,           // 현재가
                chart.getSession().getStatus().name(),   // 세션 상태(IN_PROGRESS / COMPLETED)
                cashBalance,
                positionQty,
                avgPrice,
                executedAutoExit,         // 이번 진행에서 자동청산 발생 여부
                autoExitReason            // 자동청산 사유(STOP_LOSS / TAKE_PROFIT / null)
        );
    }
}
