package com.tradenova.paper.controller;

import com.tradenova.paper.dto.PaperAccountCreateRequest;
import com.tradenova.paper.dto.PaperAccountResponse;
import com.tradenova.paper.dto.PaperAccountUpdateRequest;
import com.tradenova.paper.service.PaperAccountService;
import com.tradenova.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor //final 필드 생성자 자동 주입
@RequestMapping("/api/paper-accounts")
public class PaperAccountController {

    private final PaperAccountService paperAccountService;

    @PostMapping //계좌 생성 API
    public ResponseEntity<PaperAccountResponse> create(Authentication auth,
                                                       @RequestBody PaperAccountCreateRequest req) {
        //Spring Security 에서 인증된 사용자 정보 꺼냄
        //userId를 요청 파라미터로 받지 않음 → 보안적으로 매우 좋음
        Long userId = extractUserId(auth);

        return ResponseEntity.ok(paperAccountService.create(userId, req));
    }

    @GetMapping //계좌 목록 조회 API
    public ResponseEntity<List<PaperAccountResponse>> list(Authentication auth) {

        Long userId = extractUserId(auth);

        return ResponseEntity.ok(paperAccountService.list(userId));
    }

    @PatchMapping("/{id}/default") //기본 계좌 설정 API
    public ResponseEntity<Void> setDefault(Authentication auth, @PathVariable Long id) {

        Long userId = extractUserId(auth);

        paperAccountService.setDefault(userId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reset") //계좌 리셋 API
    public ResponseEntity<Void> reset(Authentication auth, @PathVariable Long id) {

        Long userId = extractUserId(auth);

        paperAccountService.reset(userId, id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}") // 계좌 이름/설명 수정
    public ResponseEntity<PaperAccountResponse> update(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody PaperAccountUpdateRequest req
    ) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(paperAccountService.update(userId, id, req));
    }
    // CHANGED: 공통 userId 추출 유틸 (훈련 컨트롤러들과 동일 패턴)
    private Long extractUserId(Authentication authentication) {
        Object p = authentication.getPrincipal();
        return (p instanceof Long) ? (Long) p : Long.valueOf(p.toString());
    }
}
