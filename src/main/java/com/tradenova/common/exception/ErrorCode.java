package com.tradenova.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    //400 BAD REQUEST
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "error.INVALID_REQUEST"),
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "error.EMAIL_ALREADY_EXISTS"),
    NICKNAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST,"error.NICKNAME_ALREADY_EXISTS"),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST,"error.INVALID_VERIFICATION_CODE"),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST,"error.VERIFICATION_CODE_EXPIRED"),

    // 훈련 관련
    TRAINING_SESSION_CREATE_FAILED(HttpStatus.BAD_REQUEST, "error.TRAINING_SESSION_CREATE_FAILED"),
    INVALID_TRAINING_MODE(HttpStatus.BAD_REQUEST, "error.INVALID_TRAINING_MODE"),

    //401 UNAUTHORIZED
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED,"error.UNAUTHORIZED"),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED,"error.INVALID_PASSWORD"),
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED,"error.EMAIL_NOT_VERIFIED"),

    // 403 FORBIDDEN
    ACCOUNT_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "error.ACCOUNT_LIMIT_EXCEEDED"),
    TRAINING_SESSION_FORBIDDEN(HttpStatus.FORBIDDEN, "error.TRAINING_SESSION_FORBIDDEN"),

    //404 NOT FOUND
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "error.USER_NOT_FOUND"),
    PAPER_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.PAPER_ACCOUNT_NOT_FOUND"),
    SYMBOL_NOT_FOUND(HttpStatus.NOT_FOUND, "error.SYMBOL_NOT_FOUND"),
    TRAINING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "error.TRAINING_SESSION_NOT_FOUND"),

    //409 CONFLICT
    DUPLICATE_EMAIL(HttpStatus.CONFLICT,"error.DUPLICATE_EMAIL"),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT,"error.DUPLICATE_NICKNAME"),

    // =====================
    // 502 BAD GATEWAY (외부 API - KIS)
    // =====================
    KIS_TOKEN_RESPONSE_EMPTY(HttpStatus.BAD_GATEWAY, "error.KIS_TOKEN_RESPONSE_EMPTY"),
    KIS_RESPONSE_EMPTY(HttpStatus.BAD_GATEWAY, "error.KIS_RESPONSE_EMPTY"),
    KIS_API_ERROR(HttpStatus.BAD_GATEWAY, "error.KIS_API_ERROR"),
    KIS_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "error.KIS_API_CALL_FAILED");

    //에러 코드 하나가 가지는 정보
    private final HttpStatus status; //HTTP 응답 상태 코드 (400, 404, 409 등)
    private final String messageKey; //i18n 메시지 키 (error.user.notfound 같은 값)

    //enum 생성자
    //각 ErrorCode 상수가 생성될 때
    //상태 코드 + 메시지 키를 함께 고정
    ErrorCode(HttpStatus status, String messageKey){
        this.status = status;
        this.messageKey = messageKey;
    }
}
