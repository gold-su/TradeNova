package com.tradenova.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    //400 BAD REQUEST
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid Request"),
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST,"이미 사용 중인 닉네임입니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST,"유효하지 않은 인증 코드입니다."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST,"인증 코드가 만료되었습니다."),

    //401 UNAUTHORIZED
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED,"비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED,"이메일 인증이 완료되지 않았습니다."),

    //404 NOT FOUND
    USER_NOT_FOUND(HttpStatus.NOT_FOUND,"사용자를 찾을 수 없습니다."),

    //409 CONFLICT
    DUPLICATE_EMAIL(HttpStatus.CONFLICT,"이미 존재하는 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT,"이미 존재하는 닉네임입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message){
        this.status = status;
        this.message = message;
    }
}
