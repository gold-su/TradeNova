package com.tradenova.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor // 매개변수 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 매개변수로 받는 전체 생성자 자동 생성
@Entity
@Builder
@Table(name="users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email",unique = true,nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "nickname",length = 50, nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 20)
    private SubscriptionTier subscriptionTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role; //USER / ADMIN

    @Enumerated(EnumType.STRING)
    @Column(name = "signup_type",nullable = false, length = 20)
    private SignupType signupType; // LOCAL / GOOGLE

    //선호 언어
    @Column(name = "language", length = 10)
    private String language; // 예: "ko", "en", "ja"

    @Column(name = "timezone", length = 50)
    private String timezone; // 예: "Asia/Seoul"

    // 이메일 인증 관련 --------------------------

        @Column(name = "email_verified", nullable = false)
        private boolean emailVerified;  // 기본값 : false

        @Column(name = "verification_token", length = 100)
        private String verificationToken; // UUID 등

        @Column(name = "verification_expires_at")
        private OffsetDateTime verificationExpiresAt;

    // -----------------------------------------

    @CreationTimestamp
    @Column(name = "created_at",nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status; // ACTIVE, SUSPENDED, DELETED 등



    /*
     * 로컬 회원가입용 편의 메서드
     * - created은 @CreationTimestamp에 맡김
     * - 기본 role, signupType, language, timezone 세잍
     */
    public static User createLocalUser(String email,
                                       String encodedPassword,
                                       String nickname){

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return User.builder()
                .email(email)
                .passwordHash(encodedPassword)
                .nickname(nickname)
                .subscriptionTier(SubscriptionTier.FREE)
                .role(UserRole.USER)
                .signupType(SignupType.LOCAL)
                .language("ko")         //기본값: 한국어
                .timezone("Asia/Seoul") //기본값 한국 시간대
                .emailVerified(false)
                .status(UserStatus.ACTIVE)
                .verificationToken(UUID.randomUUID().toString())
                .verificationExpiresAt(now.plusHours(24)) // 24시간 유효
                .build();
    }

    //이메일 인증 코드를 발급하는 메서드
    //이 메서드가 호출되면 "이 유저는 지금 이메일 인증 대기 상태"가 된다.
    public void issueVerificationCode(String code, OffsetDateTime expiresAt){
        this.verificationToken = code; //이메일로 보낸 인증 코드
        this.verificationExpiresAt = expiresAt; //인증 코드 만료 시간
    }

    //사용자가 이메일 인증을 성공했을 때 호출
    public void verifyEmail(){
        this.emailVerified = true;
        this.verificationToken = null;
        this.verificationExpiresAt = null;
    }

    /**
     * 로그인 성공 시 마지막 로그인 시간 갱신.
     * - UTC 기준으로 저장 (나중에 글로벌 서비스 대비)
     */
    public void touchLastLogin(){
        this.lastLoginAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
