package com.tradenova.beta;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/beta/access")
public class BetaAccessController {

    private final BetaAccessGuard betaAccessGuard;

    @GetMapping
    public ResponseEntity<BetaAccessResponse> getAccess(Authentication authentication) {
        Long userId = extractUserId(authentication);
        return ResponseEntity.ok(new BetaAccessResponse(
                betaAccessGuard.isPublicBetaEnabled(),
                betaAccessGuard.isUserAllowed(userId)
        ));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        return null;
    }
}
