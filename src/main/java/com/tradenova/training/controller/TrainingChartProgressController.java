package com.tradenova.training.controller;

import com.tradenova.training.dto.SessionAdvanceRequest;
import com.tradenova.training.dto.SessionProgressResponse;
import com.tradenova.training.service.TrainingSessionProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 차트(TrainingSessionChart) 진행(progress) API 컨트롤러
 *
 * - 멀티차트 구조에서 "세션"이 아니라 "차트 단위"로 진행 상태(progressIndex)를 전진시킨다.
 * - userId는 클라이언트가 전달하지 않고, Authentication(principal)에서 추출한다.
 *   -> 다른 유저의 차트 진행/조작을 막기 위한 기본 보안 장치
 *
 *  Base Path : /api/training/chart/{chartId}
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training/charts/{chartId}")
public class TrainingChartProgressController {

    // 차트 진행 관련 로직 서비스
    // - next/advance 수행 시 소유권 검증, 상태 변경, 현개자/잔고/포지션 스냅샷 생성 등을 담당
    private final TrainingSessionProgressService progressService;

    /**
     * 다음 봉(1 step) 진행
     *
     * POST /api/training/charts/{chartId}/next
     *
     * 흐름 :
     * 1) Authentication에서 userId 추출
     * 2) progressService.next(userId, chartId) 호출
     *    - 차트 소유권 검증 (해당 유저의 차트인지)
     *    - progressIndex + 1
     *    - 현재가/잔고/포지션 상태를 스냅샷으로 응답
     *
     * @param authentication 인증 객체(JWT 기반)
     * @param chartId        진행할 차트 ID
     * @return               진행 후 상태 스냅샷(SessionProgressResponse)
     */
    @PostMapping("/next")
    public ResponseEntity<SessionProgressResponse> next(
            Authentication authentication,
            @PathVariable Long chartId
    ) {
        // SecurityContext에 저장된 principal = userId(Long) 전제
        Long userId = extractUserId(authentication);

        // 1봉 진행 후 상태 스냅샷 반환
        return ResponseEntity.ok(progressService.next(userId, chartId));
    }

    /**
     * N봉 진행(여러 step) 진행
     *
     * POST /api/training/charts/{chartId}/advance
     *
     * Request Body:
     * - steps: 진행할 봉 개수 (Validation: 1~500 등은 DTO에서 설정)
     *
     * 흐름:
     * 1) Authentication에서 userId 추출
     * 2) progressService.advance(userId, chartId, steps) 호출
     *    - 차트 소유권 검증
     *    - progressIndex + steps (남은 봉 수 고려)
     *    - 진행 후 상태 스냅샷 반환
     *
     * @param authentication 인증 객체
     * @param chartId        진행할 차트 ID
     * @param req            N봉 진행 요청 DTO(steps)
     * @return               진행 후 상태 스냅샷(SessionProgressResponse)
     */
    @PostMapping("/advance")
    public ResponseEntity<SessionProgressResponse> advance(
            Authentication authentication,
            @PathVariable Long chartId,
            @Valid @RequestBody SessionAdvanceRequest req
    ) {
        Long userId = extractUserId(authentication);
        
        // N봉 진행 후 상태 스냅샷 반환
        return ResponseEntity.ok(progressService.advance(userId, chartId, req.steps()));
    }

    private Long extractUserId(Authentication authentication) {
        Object p = authentication.getPrincipal();
        return (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());
    }
}
