package com.tradenova.report.controller;

import com.tradenova.report.dto.TrainingEventResponse;
import com.tradenova.report.service.ReportAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 리포트 AI 분석 실행 컨트롤러
 *
 * 기능
 * - 특정 차트의 최신 snapshot 기준으로 AI 분석 실행
 * - 결과는 training_event(Type.AI)로 저장
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/charts")
public class ReportAnalysisController {

    // AI 분석 흐름 담당 서비스 주입
    private final ReportAnalysisService reportAnalysisService;

    /**
     * 최신 snapshot 기준 AI 분석 실행
     *
     * POST /api/reports/charts/{chartId}/analyze
     *
     * 흐름
     * 1. 인증 사용자 ID 추출
     * 2. chartId와 함께 서비스 호출
     * 3. AI 분석 실행
     * 4. 결과(training_event) 반환
     */
    @PostMapping("/{chartId}/analyze")
    public ResponseEntity<TrainingEventResponse> analyze(
            Authentication authentication,
            @PathVariable Long chartId
    ) {
        // 로그인 사용자 ID 추출
        Long userId = extractUserId(authentication);

        // AI 분석 실행 후 결과 반환
        return ResponseEntity.ok(
                reportAnalysisService.analyzeLatestSnapshot(userId, chartId)
        );
    }

    /**
     * Authentication 에서 userId 추출
     * 
     * JWT 인증 환경에서
     * principal에 userId가 들어있을 수 있음
     */
    private Long extractUserId(Authentication authentication) {
        Object p = authentication.getPrincipal();
        return (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());
    }
}
