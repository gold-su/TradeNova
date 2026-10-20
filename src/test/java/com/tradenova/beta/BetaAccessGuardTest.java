package com.tradenova.beta;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BetaAccessGuardTest {

    @Test
    void blocksRegularUserWhenPublicBetaIsOff() {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(1L)).thenReturn(Optional.of(user("visitor@example.com")));
        BetaAccessGuard guard = new BetaAccessGuard(
                new BetaAccessPolicy(false, "internal@example.com"), users);

        assertThatThrownBy(() -> guard.requireUserAccess(1L))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getErrorCode())
                        .isEqualTo(ErrorCode.PUBLIC_BETA_CLOSED));
    }

    @Test
    void allowsConfiguredEmailWithTrimmedCaseInsensitiveComparison() {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(2L)).thenReturn(Optional.of(user("INTERNAL@example.com")));
        BetaAccessGuard guard = new BetaAccessGuard(
                new BetaAccessPolicy(false, "  internal@EXAMPLE.com  "), users);

        assertThatCode(() -> guard.requireUserAccess(2L)).doesNotThrowAnyException();
    }

    @Test
    void publicBetaKeepsExistingFlowOpenWithoutAllowlistLookup() {
        UserRepository users = mock(UserRepository.class);
        BetaAccessGuard guard = new BetaAccessGuard(new BetaAccessPolicy(true, ""), users);

        assertThatCode(() -> guard.requireUserAccess(99L)).doesNotThrowAnyException();
        assertThat(guard.isUserAllowed(null)).isTrue();
        verifyNoInteractions(users);
    }

    @Test
    void signupIsClosedWhenPublicBetaIsOff() {
        BetaAccessGuard guard = new BetaAccessGuard(
                new BetaAccessPolicy(false, "internal@example.com"), mock(UserRepository.class));

        assertThatThrownBy(guard::requireSignupAccess)
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getErrorCode())
                        .isEqualTo(ErrorCode.PUBLIC_BETA_CLOSED));
    }

    @Test
    void wildcardAllowlistIsRejected() {
        assertThatThrownBy(() -> new BetaAccessPolicy(false, "*"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User user(String email) {
        return User.builder().email(email).build();
    }
}
