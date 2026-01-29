package com.tradenova.common.exception;

import com.tradenova.user.exception.DuplicateEmailException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * 전역 예외 처리기
 * - 모든 @RestController에서 발생한 예외를 한 곳에서 처리하여 "응답 포맷"을 통일한다.
 * - i18n(MessageSource)을 사용해서 Accept-Language에 맞는 메시지로 내려준다.
 */
@RestControllerAdvice  // 모든 RestController 에서 발생하는 예외를 가로채서 한 곳에서 처리하는 전역 예외 처리기. / 각 메서드에 ExceptionHandler 붙여서 예외 타입별로 응답을 직접 커스터마이징
@RequiredArgsConstructor
public class GlobalExceptionHandler  {

    private final MessageSource messageSource;

    /**
     * ErrorCode의 messageKey를 현재 Locale(= Accept-Language)로 해석해서 사용자에게 보여줄 문자열을 만든다.
     * - messageKey가 messages*.properties에 없으면 messageKey 자체를 fallback으로 사용한다.
     */
    private String resolveMessage(ErrorCode code) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(
                code.getMessageKey(),     // messages.properties의 key
                null,                     // 치환 파라미터 없으면 null
                code.getMessageKey(),     // 키가 없을 때 기본값(그냥 key 출력)
                locale
        );
    }

    /**
     * CustomException 처리
     * - 서비스 로직에서 의도적으로 던지는 예외의 최종 응답 포맷을 통일한다.
     * - 예: USER_NOT_FOUND, INVALID_PASSWORD, EMAIL_NOT_VERIFIED 등
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustom(CustomException ex) {
        ErrorCode code = ex.getErrorCode();

        ErrorResponse body = new ErrorResponse(
                code.getStatus().value(),   // HTTP status code number
                code.name(),                // 에러 코드 문자열(ENUM name)
                resolveMessage(code),       // locale 반영된 message
                null                        // field errors 없음
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    /**
     * DTO Validation(@Valid) 실패 처리
     * - field 별 에러를 errors로 묶어서 내려준다.
     * - 프론트에서 입력창 아래에 메시지 표시하기 좋게 만든다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        ErrorCode code = ErrorCode.INVALID_REQUEST;

        ErrorResponse body = new ErrorResponse(
                code.getStatus().value(),
                code.name(),
                resolveMessage(code),
                fieldErrors
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    /**
     * 예상하지 못한 서버 예외 처리
     * - 운영에서는 stacktrace를 그대로 출력하는 대신 로깅 프레임워크(log.error)로 남기는 것을 추천한다.
     * - 사용자에게는 내부 정보를 숨기고 공통 메시지만 내려준다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ex.printStackTrace();

        // 공통 500 에러 코드가 있으면 그걸 쓰는게 더 좋다.
        // 지금은 ErrorCode에 INTERNAL_SERVER_ERROR가 없으니 고정 문자열로 처리.
        ErrorResponse body = new ErrorResponse(
                500,
                "INTERNAL_SERVER_ERROR",
                "서버 오류가 발생했습니다.",
                null
        );

        return ResponseEntity.status(500).body(body);
    }

    /**
     * 낙관락(동시성 충돌) 처리
     * - @Version 기반 업데이트 충돌 시 발생
     * - 사용자에게 409 + i18n 메시지로 "재시도" 안내
     */
    @ExceptionHandler({ //이 메서드가 어떤 예외를 처리할지 지정하는 어노테이션, 여기서는 낙관락 관련 예외 2가지를 모두 잡음
            OptimisticLockingFailureException.class, //spring
            OptimisticLockException.class   //jpa
    })
    public ResponseEntity<ErrorResponse> handleOptimisticLock(Exception ex) {

        // 에러코드 담기
        ErrorCode code = ErrorCode.CONCURRENT_REQUEST;

        // 에러 응답 생성
        ErrorResponse body = new ErrorResponse(
                code.getStatus().value(),
                code.name(),
                resolveMessage(code),
                null
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

}
