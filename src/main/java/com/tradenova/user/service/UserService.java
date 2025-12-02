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
@Service //서비스 레이어 역할 표시, 스프링이 bean 으로 등록해서 DI(의존성 주입) 가능하게 해줌.
@RequiredArgsConstructor //final 필드 생성자 자동 생성
@Transactional // 이 클래스의 모든 메서드에 트랜잭션이 기본으로 걸림 [ DB 작업 중 에러 터지면 롤백 해주는 단위 ]
public class UserService {

    private final UserRepository userRepository; //JPA Repository 인터페이스. 유저 저장/조회, 이메일 중복 체크 등 DB 접근 역할.
    private final PasswordEncoder passwordEncoder; //스프링 시큐리티에서 제공. 비밀번호 암호화해서 저장할 때 사용.

    /*
     * 회원가입
     * - 이메일 중복 체크
     * - 비밀번호 암호화
     * - 기본 role / signupType / 언어 / 타임존 세팅
     * - 저장 후 UserResponse로 반환
     */
    public UserResponse signup(UserSignupRequest request){

        // 1) 이메일 중복 체크
        if(userRepository.existsByEmail(request.getEmail())){  //이메일이 이미 존재하는지 true/false 리턴
            throw new DuplicateEmailException(request.getEmail()); //이미 있으면 DuplicateEmailException 던져서 회원가입 실패 처리
        }

        // 2) 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword()); //평문 비밀번호 encode()로 암호화.

        // 3) User 엔티티 생성
        User user = User.builder() //User 엔티티를 빌더 패턴으로 생성.
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .nickname(request.getNickname())
                .role(UserRole.USER)
                .signupType(SignupType.LOCAL)
                .language("ko")         //기본값
                .timezone("Asia/Seoul") //기본값
                .build();

        // 4) 저장
        User saved = userRepository.save(user); //JPA 가 이 텐티리를 DB에 INSERT 해줌. / saved 안에는 PK(id)까지 포함된 완성된 User 객체가 들어있음.

        // 5) 응답 DTO로 변환 후 리턴
        return UserResponse.from(saved); //엔티티(User)를 그대로 클라이언트에게 보내지 않고 DTO로 변환해서 리턴. (passwordHash 같은 민감 정보는 빼야 함, API 응답 형태를 엔티티 구조와 분리)
    }

    /*
     * 내 정보 조회 (id 기준)
     * - 나중에 SecurityContext 에서 userId 뽑아서 사용
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true) //조회 전용으로 성능 최적화 + 변경 방지
    public UserResponse getProfile(Long userId){
        User user = userRepository.findById(userId) //userId 없으면 UserNotFoundException 던짐
                .orElseThrow(() -> new UserNotFoundException(userId));

        return UserResponse.from(user); //userId 있다면 응답 DTO로 변환해서 리턴, 나중에 실제 로그인 유저는 userId를 SecurityContext에서 꺼내 쓸 계획.
    }

    /*
     * 이메일로 User 조회 (시큐리티 / 로그인용)
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true) //조회 전용으로 성능 최적화 + 변경 방지
    public User findByEmail(String email){ //email 기준으로 User 찾고, 없으면 예외
        return userRepository.findByEmail(email) //이 메서드는 내부 서비스용이라 DTO 말고 엔티티 그대로 반환. (클라이언트에게 보내는 메서드가 아님)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    /*
     * 로그인 성공 시 마지막 로그인 시간 갱신
     */
    public void updateLastLogin(String email){
        User user = findByEmail(email); //email로 user 가져옴
        user.updateLocalUser(); //user 엔티티 안에 편의 메서드 사용, 변경감지 -> UPDATE 쿼리 날아감.
    }
}
