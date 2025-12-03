package com.tradenova.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String tokenType = "Bearer";
    private UserResponse user;
}
