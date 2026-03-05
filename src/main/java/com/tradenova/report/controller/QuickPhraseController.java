package com.tradenova.report.controller;

import com.tradenova.report.dto.QuickPhraseCreateRequest;
import com.tradenova.report.dto.QuickPhraseResponse;
import com.tradenova.report.dto.QuickPhraseUpdateRequest;
import com.tradenova.report.service.QuickPhraseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/quick-phrases")
public class QuickPhraseController {

    private final QuickPhraseService service;

    // 목록
    // GET /api/reports/quick-phrases
    @GetMapping
    public ResponseEntity<List<QuickPhraseResponse>> list(Authentication auth) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(service.list(userId));
    }

    // 생성
    // POST /api/reports/quick-phrases
    @PostMapping
    public ResponseEntity<QuickPhraseResponse> create(
            Authentication auth,
            @RequestBody QuickPhraseCreateRequest req
    ) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(service.create(userId, req));
    }

    // 수정
    // PATCH /api/reports/quick-phrases/{id}
    @PatchMapping("/{id}")
    public ResponseEntity<QuickPhraseResponse> update(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody QuickPhraseUpdateRequest req
    ) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(service.update(userId, id, req));
    }

    // 삭제
    // DELETE /api/reports/quick-phrases/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication auth,
            @PathVariable Long id
    ) {
        Long userId = extractUserId(auth);
        service.delete(userId, id);
        return ResponseEntity.ok().build();
    }

    private Long extractUserId(Authentication authentication) {
        Object p = authentication.getPrincipal();
        return (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());
    }
}
