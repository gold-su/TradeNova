package com.tradenova.user.service;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.security.JwtTokenProvider;
import com.tradenova.user.dto.*;
import com.tradenova.user.entity.User;
import com.tradenova.user.exception.DuplicateEmailException;
import com.tradenova.user.exception.UserNotFoundException;
import com.tradenova.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
    private final JwtTokenProvider jwtTokenProvider;

    //개발용: 아무 코드나 통과시키기 위해 TRUE로
    //나중에 false로 변경/삭제 or profile로 분기
    @Value("${app.dev.allow-any-verify-code:true}")
    private boolean allowAnyVerifyCode;

    private static final SecureRandom RND = new SecureRandom();

    @Transactional
    public LoginResponse login(LoginRequest request) {

        // 1) 이메일로 유저 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

//        // 2) 이메일 인증 확인
//        if(!user.isEmailVerified()) {
//            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
//        } //이메일 인증 로직 코딩 후 주석 해제

        // 3) 비밀번호 검증
        if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())){
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        //로그인 성공 시 마지막 로그인 시간 갱신.
        //User Entity에 있는 메서드 사용
        user.touchLastLogin();

        // 4) 마지막 로그인 시간 갱신
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        // 6) 응답 DTO로 변환
        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();
    }

    /*
     * 회원가입
     * - 이메일 중복 체크
     * - 비밀번호 암호화
     * - 기본 role / signupType / 언어 / 타임존 세팅
     * - 저장 후 UserResponse로 반환
     */
    public UserResponse signup(UserSignupRequest request){

        String email = request.getEmail().trim().toLowerCase();
        String nickname = request.getNickname().trim();

        // 1) 이메일 중복 체크
        if(userRepository.existsByEmail(email)){  //이메일이 이미 존재하는지 true/false 리턴
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL); //이미 있으면 CustomException(ErrorCode.DUPLICATE_EMAIL) 던져서 회원가입 실패 처리
        }

        // 2) 닉네임 중복 체크
        if(userRepository.existsByNickname(nickname)){
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }

        // 3) 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword()); //평문 비밀번호 encode()로 암호화.

        // 4) Entity 에서 팩토리 메서드 사용
        User user = User.createLocalUser(
                request.getEmail(),
                encodedPassword,
                request.getNickname()
        );

        // 5) 저장
        User saved = userRepository.save(user); //JPA 가 이 텐티리를 DB에 INSERT 해줌. / saved 안에는 PK(id)까지 포함된 완성된 User 객체가 들어있음.

        // 6) 응답 DTO로 변환 후 리턴
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


    private String generate6DigitCode(){
        int n = RND.nextInt(900000) + 100000;
        return String.valueOf(n);
    }

    @Transactional //하나의 트랜잭션으로 묶어줌, 회원가입 시에 이메일 인증 실패면 DB에 아무것도 안 남게 전부 롤백
    //이메일 인증 코드를 생성하고 User 엔티티에 저장하는 유스케이스
    public EmailSendResponse sendEmailVerification(EmailSendRequest req){

        //이메일 기준으로 User 조회, 존재하지 않으면 즉시 종료
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        //이미 인증된 경우에도 UX상 "보냈다"로 처리 가능
        if(user.isEmailVerified()){
            return new EmailSendResponse("이미 인증된 이메일입니다.", null);
        }

        //인증 코드 생성
        String code = generate6DigitCode();
        //UTC 기준으로 만료 시간 생성.
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);

        //도메인 메서드 호출
        user.issueVerificationCode(code, expiresAt);

        //TODO: 여기서 실제 이메일 발송 붙이면 됨
        //mailService.send(req.getEmail(), code);

        //지금은 개발 편의상 코드 내려줌(나중에 제거)

        return new EmailSendResponse("인증 코드가 발급되었습니다.", code);
    }

    @Transactional
    //사용자가 입력한 인증 코드를 검증하고 이메일 인증 상태로 전환
    public void verifyEmail(EmailVerifyRequest req){
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if(user.isEmailVerified()) return;

        //만료 체크
        if(user.getVerificationExpiresAt() == null || //인증 코드가 없음
                user.getVerificationExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))){ // 인증 시간이 지남
            throw new CustomException(ErrorCode.VERIFICATION_CODE_EXPIRED); //VERIFICATION_CODE_EXPIRED 예외
        }

        //개발용 : 아무 코드나 통과
        if (!allowAnyVerifyCode) {
            if (user.getVerificationToken() == null ||
                    !user.getVerificationToken().equals(req.getCode())) {
                throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
            }
        }
        user.verifyEmail();
    }

}
