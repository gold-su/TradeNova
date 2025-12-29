package com.tradenova.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

//KIS OAuth 토큰 발급 API의 JSON 응답을 담는 DTO
public record KisTokenResponse(
        //@JsonProperty는 JSON 필드명 <-> Java 변수명이 다르기 때문에 사용해야 함
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn, // 초 단위인 경우가 많음
        @JsonProperty("access_token_token_expired") String expiredAt // 문서/응답마다 다를 수 있어 Optional로 바꿔도 됨
) {
}
