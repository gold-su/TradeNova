package com.tradenova.training.controller;

import com.tradenova.training.dto.TrainingHistoryDetailResponse;
import com.tradenova.training.dto.TrainingHistorySummaryResponse;
import com.tradenova.training.service.TrainingHistoryService;
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
@RequestMapping("/api/training/sessions")
public class TrainingHistoryController {
    private final TrainingHistoryService historyService;

    @GetMapping("/history")
    public ResponseEntity<List<TrainingHistorySummaryResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(historyService.list(userId(authentication)));
    }

    @GetMapping("/{sessionId}/history")
    public ResponseEntity<TrainingHistoryDetailResponse> detail(
            Authentication authentication, @PathVariable Long sessionId) {
        return ResponseEntity.ok(historyService.detail(userId(authentication), sessionId));
    }

    private Long userId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        return principal instanceof Long id ? id : Long.valueOf(principal.toString());
    }
}
