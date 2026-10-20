package com.tradenova.beta;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class BetaAccessWebConfig implements WebMvcConfigurer {

    private final BetaAccessInterceptor betaAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(betaAccessInterceptor)
                .addPathPatterns(
                        "/api/paper-accounts/**",
                        "/api/training/**",
                        "/api/reports/**"
                );
    }
}
