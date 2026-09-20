package com.tradenova.training.controller;

import com.tradenova.training.dto.GrowthOverviewResponse;
import com.tradenova.training.service.GrowthAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training/growth")
public class GrowthAnalyticsController {
    private final GrowthAnalyticsService growthAnalyticsService;

    @GetMapping
    public ResponseEntity<GrowthOverviewResponse> overview(
            Authentication authentication,
            @RequestParam(required = false) Integer limit) {
        if (limit != null && limit != 10 && limit != 30) limit = null;
        return ResponseEntity.ok(growthAnalyticsService.overview(userId(authentication), limit));
    }

    private Long userId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        return principal instanceof Long id ? id : Long.valueOf(principal.toString());
    }
}
