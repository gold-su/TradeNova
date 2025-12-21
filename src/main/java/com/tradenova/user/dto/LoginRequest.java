package com.tradenova.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @Email(message = "{login.email.invalid}")
    @NotBlank(message = "{login.email.notblank}")
    private String email;

    @NotBlank(message = "{login.password.notblank}")
    private String password;
}
