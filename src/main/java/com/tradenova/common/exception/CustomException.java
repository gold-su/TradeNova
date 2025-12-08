package com.tradenova.common.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException { //서비스에서 공통적으로 던지는 비즈니스 예외를 표준화한 클래스

    private final ErrorCode errorCode;

    public CustomException(final ErrorCode errorCode) {
        //RuntimeException(부모 클래스)의 message 값을 ErrorCode의 메시지로 설정한다.
        // -> exception.getMessage() = errorCode.getMessage()
        super(errorCode.getMessage());

        //이 CustomException이 어떤 ErrorCode를 의미하는지 저장한다.
        // -> 전역 예외 처리기에서 status, code 이름, message를 꺼낼 때 사용
        this.errorCode = errorCode;
    }
}
