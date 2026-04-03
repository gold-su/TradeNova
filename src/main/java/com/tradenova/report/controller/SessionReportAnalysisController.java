package com.tradenova.report.controller;

import com.tradenova.report.dto.TrainingEventResponse;
import com.tradenova.report.service.SessionReportAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/sessions")
public class SessionReportAnalysisController {

    private final SessionReportAnalysisService sessionReportAnalysisService;

    @PostMapping("/{sessionId}/analyze")
    public ResponseEntity<TrainingEventResponse> analyzeSession(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(
                sessionReportAnalysisService.analyzeSession(userId, sessionId)
        );
    }

    // 마지막 세션 리포트 가져오기
    @GetMapping("/{sessionId}/ai/latest")
    public ResponseEntity<TrainingEventResponse> getLatestSessionAi(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(
                sessionReportAnalysisService.getLatestSessionAi(userId, sessionId)
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
