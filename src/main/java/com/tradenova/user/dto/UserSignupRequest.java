package com.tradenova.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입 요청 DTO.
 * - 컨트롤러에서 이 형태로 받는다.
 */
@Getter
@Setter
public class UserSignupRequest {

    @NotBlank(message = "{signup.email.notblank}")
    @Email(message = "{signup.email.invalid}")
    private String email;

    @NotBlank(message = "{signup.password.notblank}")
    @Size(min = 8, max = 64, message = "{signup.password.size}")
    private String password;

    @NotBlank(message = "{signup.nickname.notblank}")
    @Size(min = 2, max = 12, message = "{signup.nickname.size}")
    private String nickname;
}
