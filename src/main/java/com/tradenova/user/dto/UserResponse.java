package com.tradenova.user.dto;

import com.tradenova.user.entity.SignupType;
import com.tradenova.user.entity.User;
import com.tradenova.user.entity.UserRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 클라이언트로 돌려줄 때 사용할 응답 DTO.
 * (비밀번호 NO 포함)
 */
@Getter
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String nickname;
    private UserRole role;
    private SignupType signupType;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastLoginAt;

    public static UserResponse from(User user){
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .signupType(user.getSignupType())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
