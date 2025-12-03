package com.tradenova.common;

import com.tradenova.user.exception.DuplicateEmailException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice  // 전역 예외 처리 + 전역 응답 커스터마이징을 할 수 있는 스프링의 기능
public class GlobalExceptionHandler {

    // DTO @Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class) // @Valid @RequestBody를 쓸 때, DTO 유효성 검사 실패하면 발생한다. @NotBlank 등 쓸 때
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();     //메시지 본문 생성
        body.put("message", "요청 값이 올바르지 않습니다.");

        Map<String, String> errors = new HashMap<>();     //에러 본문 생성
        for(FieldError fieldError : ex.getBindingResult().getFieldErrors()) { //에러 본문 모으기
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        body.put("errors", errors); //에러 바디에 추가

        return ResponseEntity.badRequest().body(body); //에러 JSON BODY 변환
    }

    // 이메일 중복 같은 비즈니스 예외
    @ExceptionHandler(DuplicateEmailException.class)  // 중복 이메일이면 예외 후 이 핸들러 실행
    public ResponseEntity<Map<String, Object>> handleDuplicateEmail(DuplicateEmailException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", "ex.getMessage(): " + ex.getMessage());     //DuplicateEmailException 메시지와 이메일 응답용
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);   //이메일 중복 오류 상태 전달
    }
}
