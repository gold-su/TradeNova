package com.tradenova.training.controller;

import com.tradenova.training.dto.SessionAdvanceRequest;
import com.tradenova.training.dto.SessionProgressResponse;
import com.tradenova.training.service.TrainingSessionProgressService;
import com.tradenova.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(progressService.next(user.getId(), sessionId));
    }

    // N 봉 진행
    @PostMapping("/advance")
    public ResponseEntity<SessionProgressResponse> advance(
            @AuthenticationPrincipal User user,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionAdvanceRequest req
    ) {
        return ResponseEntity.ok(progressService.advance(user.getId(), sessionId, req.steps()));
    }
}
