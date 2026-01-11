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

    private final TrainingSessionRepository sessionRepo;
    private final TrainingRiskRuleRepository riskRepo;

    @Transactional(readOnly = true)
    public RiskRuleResponse get(Long userId, Long sessionId) {
        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        Long accountId = s.getAccount().getId();

        return riskRepo.findBySessionIdAndAccountId(sessionId, accountId)
                .map(this::toResponse)
                .orElse(new RiskRuleResponse(null, sessionId, accountId, null, null, false, null));

    }

    @Transactional
    public RiskRuleResponse upsert(Long userId, Long sessionId, RiskRuleUpsertRequest req) {
        TrainingSession s = sessionRepo.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRAINING_SESSION_NOT_FOUND));

        // 간단 검증 (원하면 더 빡세게 가능)
        if (req.stopLossPrice() != null && req.stopLossPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_RISK_RULE);
        }
        if (req.takeProfitPrice() != null && req.takeProfitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_RISK_RULE);
        }

        TrainingRiskRule rule = riskRepo.findBySessionIdAndAccountId(sessionId, s.getAccount().getId())
                .orElseGet(() -> TrainingRiskRule.builder()
                        .sessionId(sessionId)
                        .accountId(s.getAccount().getId())
                        .build());

        // 값 반영 (null이면 null로 저장 = “미설정”)
        rule.setStopLossPrice(req.stopLossPrice());
        rule.setTakeProfitPrice(req.takeProfitPrice());
        rule.setEnabled(req.autoExitEnabled() != null && req.autoExitEnabled());

        TrainingRiskRule saved = riskRepo.save(rule);
        return toResponse(saved);
    }


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
