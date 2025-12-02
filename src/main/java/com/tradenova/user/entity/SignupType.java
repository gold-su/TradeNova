package com.tradenova.user.entity;

/*
    * 가입 유형.
    * - LOCAL : 이메일 + 비밀번호로 가입
    * - GOOGLE : 구글 로그인 (추후 API 적용)
 */
public enum SignupType {
    LOCAL,
    GOOGLE
}
