package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.training.dto.SessionProgressResponse;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.entity.TrainingStatus;
import com.tradenova.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TrainingSessionProgressService {

    private final TrainingSessionRepository sessionRepo;
    private final TrainingAutoExitService autoExitService;

    @Transactional
    public SessionProgressResponse next(Long userId, Long sessionId) {
        return advance(userId, sessionId, 1);
    }


    @Transactional
    public SessionProgressResponse advance(Long userId, Long sessionId, int steps){

        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        if(s.getStatus() == TrainingStatus.FINISHED){
            // 이미 종료된 세션 진행 불가 (원하면 에러코드 추가)
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        int maxIdx = s.getBars() - 1;
        int nextIdx = Math.min(s.getProgressIndex() + steps, maxIdx);
        s.setProgressIndex(nextIdx);

        // bars-1 도달 시 자동 종료 (정책)
        if(nextIdx >= maxIdx){
            s.setStatus(TrainingStatus.FINISHED);
        }

        // 진횅 직후 자동매도 체크(룰 enabled면 전량 청산 시도)
        TrainingAutoExitService.AutoExitResult r = autoExitService.checkAndAutoExit(userId, s);

        BigDecimal currentPrice = r.currentPrice();

        return new SessionProgressResponse(
                s.getId(),
                s.getProgressIndex(),
                currentPrice,
                s.getStatus().name(),
                r.autoExited(),
                r.reason()
        );
    }
}
