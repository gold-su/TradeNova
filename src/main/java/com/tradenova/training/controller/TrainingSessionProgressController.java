package com.tradenova.training.controller;

import com.tradenova.training.dto.SessionAdvanceRequest;
import com.tradenova.training.dto.SessionProgressResponse;
import com.tradenova.training.service.TrainingSessionProgressService;
import com.tradenova.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training/sessions/{sessionId}")
public class TrainingSessionProgressController {

    private final TrainingSessionProgressService progressService;

    // 한 봉 진행
    @PostMapping("/next")
    public ResponseEntity<SessionProgressResponse> next(
            Authentication authentication,
            @PathVariable Long sessionId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(progressService.next(userId, sessionId));
    }

    // N 봉 진행
    @PostMapping("/advance")
    public ResponseEntity<SessionProgressResponse> advance(
            Authentication authentication,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionAdvanceRequest req
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(progressService.advance(userId, sessionId, req.steps()));
    }
}
