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

    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @NotBlank(message = "이메일은 필수입니다.")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "올바른 이메일 형식이어야 합니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min=8, max = 64, message = "비밀번호는 8~64자 사이여야 합니다.")
    @Pattern(regexp = "^(?=.*[^A-Za-z0-9])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자여야 합니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 50, message = "닉네임은 2~20자 사이여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]{2,15}$", message = "닉네임은 특수문자를 제외한 2~15자여야 합니다.")
    private String nickname;
}
