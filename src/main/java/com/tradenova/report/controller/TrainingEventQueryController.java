package com.tradenova.report.controller;

import com.tradenova.report.dto.TrainingEventResponse;
import com.tradenova.report.service.TrainingEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/events")
public class TrainingEventQueryController {

    private final TrainingEventService trainingEventService;

    // 이벤트 단건
    // GET /api/reports/events/{eventId}
    @GetMapping("/{eventId}")
    public ResponseEntity<TrainingEventResponse> getOne(
            Authentication auth,
            @PathVariable Long eventId
    ) {
        // 인증 객체에서 userId 추출
        Long userId = extractUserId(auth);
        // 서비스 호출
        return ResponseEntity.ok(trainingEventService.getOne(userId, eventId));
    }

    // Authentication에서 userId 꺼내는 유틸
    private Long extractUserId(Authentication authentication) {
        // JWT 필터에서 넣은 값
        Object p = authentication.getPrincipal();
        // 안전하게 Long 변환
        return (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());
    }
}
