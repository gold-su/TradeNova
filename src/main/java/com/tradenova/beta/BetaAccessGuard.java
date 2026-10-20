package com.tradenova.beta;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BetaAccessGuard {

    private final BetaAccessPolicy policy;
    private final UserRepository userRepository;

    public void requireSignupAccess() {
        if (!policy.isPublicBetaEnabled()) {
            throw new CustomException(ErrorCode.PUBLIC_BETA_CLOSED);
        }
    }

    public void requireUserAccess(Long userId) {
        if (!isUserAllowed(userId)) {
            throw new CustomException(ErrorCode.PUBLIC_BETA_CLOSED);
        }
    }

    public boolean isUserAllowed(Long userId) {
        if (policy.isPublicBetaEnabled()) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> policy.isAllowedEmail(user.getEmail()))
                .orElse(false);
    }

    public boolean isPublicBetaEnabled() {
        return policy.isPublicBetaEnabled();
    }
}
