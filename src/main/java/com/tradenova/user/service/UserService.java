package com.tradenova.user.service;

import com.tradenova.user.dto.UserResponse;
import com.tradenova.user.dto.UserSignupRequest;
import com.tradenova.user.entity.SignupType;
import com.tradenova.user.entity.User;
import com.tradenova.user.entity.UserRole;
import com.tradenova.user.exception.DuplicateEmailException;
import com.tradenova.user.exception.UserNotFoundException;
import com.tradenova.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * user 관련 비즈니스 로직
 * - 회원가입
 * - 로그인 (비밀번호 검증)
 * - 내 정보 조회
 * - 비밀번호 찾기 및 변경
 */
@Service
@RequiredArgsConstructor //final 필드 생성자 자동 생성
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /*
     * 회원가입
     * - 이메일 중복 체크
     * - 비밀번호 암호화
     * - 기본 role / signupType / 언어 / 타임존 세팅
     * - 저장 후 UserResponse로 반환
     */
    public UserResponse signup(UserSignupRequest request){

        // 1) 이메일 중복 체크
        if(userRepository.existsByEmail(request.getEmail())){
            throw new DuplicateEmailException(request.getEmail());
        }

        // 2) 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 3) User 엔티티 생성
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .nickname(request.getNickname())
                .role(UserRole.USER)
                .signupType(SignupType.LOCAL)
                .language("ko")         //기본값
                .timezone("Asia/Seoul") //기본값
                .build();

        // 4) 저장
        User saved = userRepository.save(user);

        // 5) 응답 DTO로 변환 후 리턴
        return UserResponse.from(saved);
    }

    /*
     * 내 정보 조회 (id 기준)
     * - 나중에 SecurityContext 에서 userId 뽑아서 사용
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public UserResponse getProfile(Long userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        return UserResponse.from(user);
    }

    /*
     * 이메일로 User 조회 (시큐리티 / 로그인용)
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public User findByEmail(String email){
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    /*
     * 로그인 성공 시 마지막 로그인 시간 갱신
     */
    public void updateLastLogin(String email){
        User user = findByEmail(email);
        user.updateLocalUser(); //엔티티 안에 편의 메서드 사용
    }
}
