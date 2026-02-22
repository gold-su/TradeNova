package com.tradenova.training.controller;

import com.tradenova.kis.dto.CandleDto;
import com.tradenova.training.service.TrainingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training/charts")
public class TrainingChartCandlesController {

    private final TrainingSessionService trainingSessionService;

    /**
     * 세션 기반 캔들 조회
     * GET /api/training/sessions/{chartId}/candles
     *
     * 특징:
     * - symbol/from/to 를 클라이언트가 직접 주지 않는다.
     * - chartId만 받고, 서버가 세션에 저장된 (symbol, 기간)을 기준으로 KIS 조회한다.
     *   → 프론트 조작으로 "다른 기간/다른 종목" 조회 방지
     */
    @GetMapping("/{chartId}/candles")
    public ResponseEntity<List<CandleDto>> candles(
            Authentication authentication,        //로그인 사용자 정보 (인증 객체)
            @PathVariable Long chartId               //조회할 세션 ID
    ) {
        //로그인된 사용자 ID 추출
        Long userId = extractUserId(authentication);
        //세션 소유권 검증(findByIdAndUserId) 포함해서 캔들 조회
        return ResponseEntity.ok(trainingSessionService.getChartCandles(userId, chartId));
    }

    private Long extractUserId(Authentication authentication) {
        Object p = authentication.getPrincipal();
        return (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());
    }
}
