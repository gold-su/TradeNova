package com.tradenova.training.controller;

import com.tradenova.kis.dto.CandleDto;
import com.tradenova.training.dto.TrainingSessionCreateRequest;
import com.tradenova.training.dto.TrainingSessionCreateResponse;
import com.tradenova.training.service.TrainingSessionService;
import com.tradenova.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal User user,                   //로그인 사용자 정보(인증 객체)
            @Valid @RequestBody TrainingSessionCreateRequest req  //요청 DTO (Validation 적용)
    ){
        //로그인된 사용자(Principal)에서 userId 추출
        Long userId = user.getId();

        //서비스 호출 -> 세션 생성 -> 응답 DTO 반환
        return ResponseEntity.ok(trainingSessionService.createSession(userId, req));
    }

    /**
     * 세션 기반 캔들 조회
     * GET /api/training/sessions/{sessionId}/candles
     *
     * 특징:
     * - symbol/from/to 를 클라이언트가 직접 주지 않는다.
     * - sessionId만 받고, 서버가 세션에 저장된 (symbol, 기간)을 기준으로 KIS 조회한다.
     *   → 프론트 조작으로 "다른 기간/다른 종목" 조회 방지
     */
    @GetMapping("/{sessionId}/candles")
    public ResponseEntity<List<CandleDto>> candles(
            @AuthenticationPrincipal User user,        //로그인 사용자 정보 (인증 객체)
            @PathVariable Long sessionId               //조회할 세션 ID
    ) {
        //로그인된 사용자 ID 추출
        Long userId = user.getId();
        //세션 소유권 검증(findByIdAndUserId) 포함해서 캔들 조회
        return ResponseEntity.ok(trainingSessionService.getSessionCandles(userId, sessionId));
    }

}
