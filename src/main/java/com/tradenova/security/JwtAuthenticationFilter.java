package com.tradenova.security;

import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component   //스프링 빈 등록
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter { //OncePerRequestFilter 0-> 요청당 한 번만 실행되는 필터

    private final JwtTokenProvider jwtTokenProvider; // -> 토크 파싱/검증 담당
    private final UserRepository userRepository; // -> 토큰 속 이메일로 유저 조회

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = resolveToken(request); // resolveToken(request) -> 요청 헤더에서 Authorization : Bearer xxx 꺼냄

        if(token != null && jwtTokenProvider.validateToken(token)) { //token이 있고 validateToken 통과하면
            String email = jwtTokenProvider.getEmail(token); //-> 토큰에서 이메일 꺼냄 (getEmail(token) = subject)

            Optional<User> optionalUser = userRepository.findByEmail(email); // email을 findByEmail로 실제 DB에 있는지 유저 조회 / optional은 null 일 수도 있고, 아닐 수도 있는 값을 감싸놓은 박스, 일반 객체로 만들었을 때 null이라면 다른 .getName() 형식 등의 코드가 있을 때 예외 터짐
            if(optionalUser.isPresent()){ //isPresent는 Optional 클래스의 메서드, Optional 안에 값이 있으면 true, 없으면 false
                User user = optionalUser.get(); //값이 있으니까 get 으로 실제 User 엔티티 꺼내오기

                /*
                 * new UsernamePasswordAuthenticationToken(principal, credentials, authorities)
                 * principal → user → “이 요청의 주인(로그인한 사용자)은 이 User 객체다”
                 * credentials → null → 자격 증명(비밀번호). 여기서는 더 이상 필요 없어서 null
                 * authorities → null → 사용자의 권한 목록(ROLE_USER, ROLE_ADMIN 등). 지금은 아직 권한을 안 넣어줘서 null로 둔 상태.
                 */
                // “이 User를 주체(principal)로 하는 인증 객체(auth)를 하나 만들자.
                var auth = new UsernamePasswordAuthenticationToken(
                        user, null, null
                );
                //이제부터 이 요청은 이 사용자(auth) 로 인증된 상태라고 스프링 시큐리티에게 알려줘라
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    /*
     * Authorization 헤더에서 Bearer 토큰 꺼내기
     */
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");

        if(bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
