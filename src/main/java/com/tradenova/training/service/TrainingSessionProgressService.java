package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.paper.entity.PaperPosition;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.paper.repository.PaperPositionRepository;
import com.tradenova.training.dto.SessionProgressResponse;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor    //final 필드 자동 생성자
public class TrainingSessionProgressService {

    // 훈련 세션 DB 접근용 레포지토리
    private final TrainingSessionRepository sessionRepo;
    // 자동청산(리스크 룰) 판단 로직을 담당하는 서비스
    private final TrainingAutoExitService autoExitService;

    private final PaperPositionRepository positionRepo;

    /**
     * 한 봉(candle)만 진행시키는 API
     * - 내부적으로 advance(..., 1)을 호출
     */
    @Transactional //트랜잭션
    public SessionProgressResponse next(Long userId, Long sessionId) {
        return advance(userId, sessionId, 1);
    }

    /**
     * N 봉을 한 번에 진행시키는 메서드
     * - userId로 세션 소유권 검증
     * - progressIndex 증가
     * - 종료 조건 체크
     * - 자동청산(리스크 룰) 검사
     */
    @Transactional
    public SessionProgressResponse advance(Long userId, Long sessionId, int steps){

        // 1) 세션 조회 + 소유권 검증
        //      - 남의 세션 접근 방지
        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
        // 2) 이미 종료된(COMPLETED) 세션이면 더 이상 진행 불가
        if(s.getStatus() == TrainingStatus.COMPLETED){
            // 이미 종료된 세션 진행 불가 (원하면 에러코드 추가)
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        // 3) 세션의 마지막 캔들 인덱스
        //    예: bars = 100 -> maxIdx = 99
        int maxIdx = Math.max(0, s.getBars() - 1);
        // 4) 현재 progressIndex
        //    - 아직 한 번도 진행 안 했으면 null -> 0으로 처리
        int cur = (s.getProgressIndex() == null) ? 0 : s.getProgressIndex();
        // 5) 요청된 steps 검증
        //    - 0 이하로 넘기는 건 의미 없으므로 거부
        if (steps <= 0) throw new CustomException(ErrorCode.INVALID_REQUEST);
        // 6) 다음 progressIndex 계산
        //    - cur + steps 만큼 앞으로 가되
        //    - 마지막 캔들(maxIdx)을 넘지 않도록 보정
        int nextIdx = Math.min(cur + steps, maxIdx);

        // 7) 세션에 새로운 진행 인덱스 반영
        s.setProgressIndex(nextIdx);

        // 8) 마지막 캔들(maxIdx)에 도달했으면 세션 자동 종료
        //    - 훈련은 "모든 봉을 다 본 시점"에 끝난다
        if(nextIdx >= maxIdx){
            s.setStatus(TrainingStatus.COMPLETED);
        }

        // 9) 진행 직후 자동청산(리스크 룰) 체크
        //    - stop loss / take profit 조건 충족 여부 판단
        //    - 지금 단계에서는 "실제 매도"가 아니라
        //    - autoExited 여부와 사유만 계산
        TrainingAutoExitService.AutoExitResult r = autoExitService.checkAndAutoExit(userId, s);

        // 10) 현재가 (progressIndex 기준 close 가격)
        BigDecimal currentPrice = r.currentPrice();

        // 트레이드/자동청산 이후의 “최종 스냅샷”을 내려줌
        Long accountId = s.getAccount().getId();
        Long symbolId = s.getSymbol().getId();

        // 현금 (PaperAccount에 맞는 getter로 바꾸기)
        BigDecimal cashBalance = s.getAccount().getCashBalance();

        PaperPosition pos = positionRepo.findByAccount_IdAndSymbolId(accountId, symbolId)
                .orElse(null);

        BigDecimal positionQty = (pos == null) ? BigDecimal.ZERO : pos.getQuantity();
        BigDecimal avgPrice = (pos == null) ? BigDecimal.ZERO : pos.getAvgPrice();


        // 11) 프론트로 내려줄 진행 결과 응답 DTO 생성
        return new SessionProgressResponse(
                s.getId(),              // 세션 ID
                s.getProgressIndex(),   // 현재 진행 인덱스
                currentPrice,           // 현재가
                s.getStatus().name(),   // 세션 상태(IN_PROGRESS / COMPLETED)
                cashBalance,
                positionQty,
                avgPrice,
                r.autoExited(),         // 이번 진행에서 자동청산 발생 여부
                r.reason()              // 자동청산 사유(STOP_LOSS / TAKE_PROFIT / null)
        );
    }
}
