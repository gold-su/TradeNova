package com.tradenova.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

//KIS OAuth 토큰 발급 API의 JSON 응답을 담는 DTO
public record KisTokenResponse( //토큰 요청 → “토큰 그 자체 + 토큰 설명서”를 한 번에 돌려줌
        //@JsonProperty는 JSON 필드명 <-> Java 변수명이 다르기 때문에 사용해야 함
        @JsonProperty("access_token") String accessToken, //snake_case → camelCase 매핑
        @JsonProperty("token_type") String tokenType, //Authorization 헤더 만들 때 사용
        @JsonProperty("expires_in") long expiresIn, // 토큰 유효 기간 (초 단위가 많음)
        @JsonProperty("access_token_token_expired")
        Optional<String> expiredAt // 언제 토큰 유효 기간이 죽는지 알려줄 수도 있음 / 문서/응답마다 다를 수 있어서 Optional(값이 있을수도, 없을수도 있다)

) {
}
