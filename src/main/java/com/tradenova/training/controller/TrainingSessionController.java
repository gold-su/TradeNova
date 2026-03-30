package com.tradenova.training.controller;

import com.tradenova.training.dto.*;
import com.tradenova.training.entity.TrainingSession;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.training.service.TrainingSessionService;
import com.tradenova.training.service.TrainingTradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training/sessions")
/**
 * 트레이딩 훈련 세션 API 컨트롤러
 *
 * 역할:
 * 1) 훈련 세션 생성
 * 2) 세션 기반 캔들 데이터 조회
 *
 * 특징:
 * - userId는 클라이언트가 주는 게 아니라 Authentication(principal)에서 꺼낸다
 *   → 다른 유저 세션/계좌 접근을 막기 위한 기본 보안 장치
 */
public class TrainingSessionController {

    // 훈련 세션 생성/조회 비즈니스 로직 서비스
    private final TrainingSessionService trainingSessionService;
    private final TrainingTradeService tradeService;
    private final TrainingSessionRepository sessionRepo;
    /**
     * 훈련 세션 생성
     * POST /api/training/sessions
     *
     * 요청(req):
     * - accountId: 어떤 모의투자 계좌로 훈련할지
     * - mode: 훈련 모드(RANDOM 등)
     * - bars: 차트에 사용할 봉 개수
     *
     * 응답:
     * - 세션 생성 결과(세션ID, 종목정보, 기간, bars, status 등)
     */
    @PostMapping
    public ResponseEntity<TrainingSessionCreateResponse> create(
            Authentication authentication,                   //로그인 사용자 정보(인증 객체)
            @Valid @RequestBody TrainingSessionCreateRequest req  //요청 DTO (Validation 적용)
    ){
        //로그인된 사용자(Principal)에서 userId 추출
        Long userId = extractUserId(authentication);

        //서비스 호출 -> 세션 생성 -> 응답 DTO 반환
        return ResponseEntity.ok(trainingSessionService.createSession(userId, req));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDetailResponse> getSession(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(trainingSessionService.getSession(userId, sessionId));
    }

    @GetMapping("/{sessionId}/charts")
    public ResponseEntity<List<ChartSummaryResponse>> getSessionCharts(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(trainingSessionService.getSessionCharts(userId, sessionId));
    }

    /**
     * 세션 수동 종료
     * POST /api/training/sessions/{sessionId}/finish
     */
    @PostMapping("/{sessionId}/finish")
    public ResponseEntity<SessionFinishResponse> finish(
            Authentication authentication,
            @PathVariable Long sessionId
    ){
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(trainingSessionService.finishSession(userId, sessionId));
    }

    /**
     * 현재 진행 중인 가장 최근 세션 조회
     */
    @GetMapping("/active")
    public ResponseEntity<ActiveTrainingSessionResponse> getActiveSession(Authentication authentication) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(trainingSessionService.getActiveSession(userId));
    }

    private Long extractUserId(Authentication authentication) {
        Object p = authentication.getPrincipal();
        return (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());
    }
}
