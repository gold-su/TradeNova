package com.tradenova.training.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.training.dto.RiskRuleResponse;
import com.tradenova.training.dto.RiskRuleUpsertRequest;
import com.tradenova.training.entity.TrainingRiskRule;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.repository.TrainingRiskRuleRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TrainingRiskRuleService {

    //세션 소유권 검즈용 (sessionId + userId)
    private final TrainingSessionRepository sessionRepo;
    //리스크 룰 조회/저장
    private final TrainingRiskRuleRepository riskRepo;

    /**
     * 리스크 룰 조회
     * - 룰이 없으면 "미설정" 상태의 기본 응답을 내려준다 (프론트 UX 편함)
     */
    @Transactional(readOnly = true)
    public RiskRuleResponse get(Long userId, Long sessionId) {

        // 1) 세션 조회 + 소유권 검증 (내 세션만 접근)
        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // 2) 세션에서 accountId 추출 (연관관계라 getAccount().getId())
        Long accountId = s.getAccount().getId();

        // 3) 리스크 롤 조회 (세션 + 계좌 기준)
        return riskRepo.findBySessionIdAndAccountId(sessionId, accountId)
                .map(this::toResponse)
                // 4) 룰이 없으면 "아직 설정 안함" 기본값 반환
                .orElse(new RiskRuleResponse(
                        null, //ruleId(없으니 null)
                        sessionId, //sessionId
                        accountId, //accountId
                        null, //stopLossPrice: 미설정
                        null, //takeProfitPrice : 미설정
                        false, //enabled: 꺼짐
                        null   //updatedAt: 없음
                ));

    }

    /**
     * 리스크 룰 생성/수정 (upsert)
     * - 있으면 update
     * - 없으면 insert
     */
    @Transactional
    public RiskRuleResponse upsert(Long userId, Long sessionId, RiskRuleUpsertRequest req) {
        
        // 1) 세션 조회 + 소유권 검증
        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // 간단 검증 : 가격이 들어오면 0보다 커야 한다.
        if (req.stopLossPrice() != null && req.stopLossPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_RISK_RULE);
        }
        if (req.takeProfitPrice() != null && req.takeProfitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_RISK_RULE);
        }

        //(선택) 손절/익절 관계 검증은 "현재가"가 있어야 정확함.
        // 일단 MVP 에서는 값 자체(>0)만 검증해도 충분.

        // 3) 기준 룰 있으면 가져오고, 없으면 새로 만든다.
        TrainingRiskRule rule = riskRepo.findBySessionIdAndAccountId(sessionId, s.getAccount().getId())
                .orElseGet(() -> TrainingRiskRule.builder()
                        .sessionId(sessionId)
                        .accountId(s.getAccount().getId()) //기본은 비활성
                        .build());

        // 값 반영 (null 이면 null로 저장 = “미설정”)
        rule.setStopLossPrice(req.stopLossPrice());
        rule.setTakeProfitPrice(req.takeProfitPrice());

        //enabled 값은 request가 null일 수도 있으니 안전 처리
        rule.setEnabled(req.autoExitEnabled() != null && req.autoExitEnabled());

        // 5) 저장
        TrainingRiskRule saved = riskRepo.save(rule);
        
        // 6) 응답 DTO로 변환
        return toResponse(saved);
    }

    /**
     * 엔티티 -> 응답 DTO 변환
     */
    private RiskRuleResponse toResponse(TrainingRiskRule r) {
        return new RiskRuleResponse(
                r.getId(),
                r.getSessionId(),
                r.getAccountId(),
                r.getStopLossPrice(),
                r.getTakeProfitPrice(),
                r.isEnabled(),
                r.getUpdatedAt()
        );
    }
}
