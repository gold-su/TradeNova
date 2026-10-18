package com.tradenova.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void parsesCommaSeparatedExactOriginsAndKeepsCredentialsEnabled() {
        SecurityConfig securityConfig = new SecurityConfig(mock(JwtAuthenticationFilter.class));

        var source = securityConfig.corsConfigurationSource(
                "http://localhost:3000, http://localhost:5173, https://app.example.com"
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly(
                "http://localhost:3000",
                "http://localhost:5173",
                "https://app.example.com"
        );
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void rejectsWildcardOriginsWhenCredentialsAreEnabled() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SecurityConfig.parseAllowedOrigins("https://app.example.com, *"))
                .withMessageContaining("Wildcard CORS origins");
    }

    @Test
    void removesBlankAndDuplicateOrigins() {
        assertThat(SecurityConfig.parseAllowedOrigins(
                " https://app.example.com, ,https://app.example.com "
        )).isEqualTo(List.of("https://app.example.com"));
    }
}
