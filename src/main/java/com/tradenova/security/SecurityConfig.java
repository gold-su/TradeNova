package com.tradenova.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity //웹 요청(HTTP 요청)에 대해 보안 필터 체인을 적용하라는 명령
@RequiredArgsConstructor //final 필드를 자동으로 생성자 주입해줌
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter; //JWT를 검증하는 커스텀 필터, 모든 요청이 컨트롤러로 들어가기 전에 실행됨

    @Bean
    public PasswordEncoder passwordEncoder() {
        //비밀번호 평문을 암호화할 때 사용할 구현체
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors ->{})
                .csrf(csrf -> csrf.disable()) //REST API라 CSRF 비활성화 (세션/쿠기 기반 인증이 아니므로)
                .formLogin(form -> form.disable()) // 폼 로그인 안 씀
                .httpBasic(basic -> basic.disable()) //브라우저 팝업으로 뜨는 Basic 인증 안 씀
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/auth/login",
                                "/api/auth/signup",
                                "/health"
                        ).permitAll() // 회원가입/로그인은 허용
                        .anyRequest().authenticated() // 일단 전부 허용
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class); //모든 요청 앞단에서 JWT를 검사하겠다. 기본 인증 필터보다 먼저 실행해야 하므로 Before() 사용.

        return http.build(); //SecurityFitterChain 객체를 생성하여 스프링에 전달. 이 체인을 가지고 모든 요청에 대한 보안 적용을 시작함.
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000","http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE","OPTIONS","PATCH","HEAD","TRACE","CONNECT"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
