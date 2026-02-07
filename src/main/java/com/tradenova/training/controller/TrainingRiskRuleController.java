package com.tradenova.training.controller;

import com.tradenova.training.dto.RiskRuleResponse;
import com.tradenova.training.dto.RiskRuleUpsertRequest;
import com.tradenova.training.service.TrainingRiskRuleService;
import com.tradenova.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training/sessions/{sessionId}/risk-rule")
public class TrainingRiskRuleController {

    private final TrainingRiskRuleService service;


    /**
     * 리스크 규칙 조회
     * GET /api/training/sessions/{sessionId}/risk-rule
     *
     * - 세션 소유권 검증은 Service에서 수행 (findByIdAndUserId)
     * - 룰이 없으면 "미설정 상태" 기본 응답을 내려주는 방식
     */
    @GetMapping
    public ResponseEntity<RiskRuleResponse> get(
            Authentication authentication,   // 로그인 사용자(Principal)
            @PathVariable Long sessionId          // 조회할 훈련 세션 ID
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(service.get(userId, sessionId));
    }

    /**
     * 리스크 규칙 생성/수정 (Upsert)
     * PUT /api/training/sessions/{sessionId}/risk-rule
     *
     * - 기존 룰이 있으면 수정, 없으면 생성
     * - stopLossPrice / takeProfitPrice / enabled 값을 저장
     */
    @PutMapping
    public ResponseEntity<RiskRuleResponse> upsert(
            Authentication authentication,       // 로그인 사용자(Principal)
            @PathVariable Long sessionId,             // 대상 훈련 세션 ID
            @Valid @RequestBody RiskRuleUpsertRequest req // 요청 DTO (검증 적용)
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(service.upsert(userId, sessionId, req));
    }
}
