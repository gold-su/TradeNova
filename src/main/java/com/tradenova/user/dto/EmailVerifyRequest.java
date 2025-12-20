package com.tradenova.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EmailVerifyRequest {

    @NotNull
    @Email
    private String email;

    @NotNull
    private String code; //6자리
}
