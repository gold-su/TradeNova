package com.tradenova.security;

import com.tradenova.user.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j     //로그 찍을 수 있는 log 객체를 자동으로 만들어주는 Lombok 어노테이션     //log.info() 같은 로그 기능 자동 주입
@Component //스프링에게 "이 클래스를 빈으로 등록해서 관리해줘"라고 알리는 어노테이션  //스프링 IoC 컨테이너에 빈으로 등록 (DI 가능)
public class JwtTokenProvider {

    private final UserDetailsManager userDetailsManager;
    @Value("${tradenova.jwt.secret}")
    private String secret;

    @Value("${tradenova.jwt.access-token-validity-in-seconds}")
    private long accessTokenValiditySeconds;

    private Key key;

    public JwtTokenProvider(UserDetailsManager userDetailsManager) {
        this.userDetailsManager = userDetailsManager;
    }

    @PostConstruct //스프링 빈이 생성되고 DI(의존성 주입)까지 모두 끝난 직후에 딱 한 번 실행되는 메서드에 붙이는 어노테이션이야.
    public void init(){
        //문자열 시크릿을 HMAC-SHA 키로 변환
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /*
     * 엑세스 토큰 생성
     */
    public String generateAccessToken(User user){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenValiditySeconds * 1000);

        return Jwts.builder()
                .setSubject(user.getEmail()) //주체: 이메일
                .claim("role", user.getRole().name()) //커스텀 클레임
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /*
     * 토큰에서 Authentication 객체 생성
     * (UserDetailsService 연동하면 더 좋음, 지금은 간단버전으로 이메일만 사용해도 됨)
     */
    public Authentication getAuthentication(String token, UserDetails userDetails){
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    /*
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token){
        try{
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e){
            log.info("JWT expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            log.info("Invalid JWT", e);
        }
        return false;
    }

    /*
     * 토큰에서 이메일(subject) 꺼내기
     */
    public String getEmail(String token){
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }
}
