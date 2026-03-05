package com.tradenova.report.controller;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.report.dto.TrainingEventResponse;
import com.tradenova.report.service.TrainingEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/charts")
public class TrainingEventController {

    // Event 서비스
    private final TrainingEventService trainingEventService;

    // 최신 이벤트 N개
    // GET /api/reports/events/charts/{chartId}?size=50
    @GetMapping("/{chartId}/events")
    public ResponseEntity<List<TrainingEventResponse>> getLatestEvents( // 반환 타입
            Authentication auth,        // 현재 사용자
            @PathVariable Long chartId, // URL에서 chartId 가져오기
            @RequestParam(defaultValue = "50") int size // 없으면 기본 값 50
    ){
        // size 검증
        if (size < 1 || size > 200) {
            throw new CustomException(ErrorCode.INVALID_EVENT_LIST_SIZE);
        }
        // JWT 인증에서 userId 추출
        Long userId = extractUserId(auth);
        // 서비스 호출, 조건으로 최신 이벤트 조회
        return ResponseEntity.ok(trainingEventService.listLatest(userId, chartId, size));
    }

    // Authentication에서 userId 꺼내는 유틸
    private Long extractUserId(Authentication authentication) {
        // JWT 필터에서 넣은 값
        Object p = authentication.getPrincipal();
        // 안전하게 Long 변환
        return (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());
    }
}
