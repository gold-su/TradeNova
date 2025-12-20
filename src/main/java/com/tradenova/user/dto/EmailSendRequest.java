package com.tradenova.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EmailSendRequest {
    @NotBlank
    @Email
    private String email;
}
