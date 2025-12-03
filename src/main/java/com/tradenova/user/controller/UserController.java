package com.tradenova.user.controller;


import com.tradenova.user.dto.LoginRequest;
import com.tradenova.user.dto.LoginResponse;
import com.tradenova.user.dto.UserResponse;
import com.tradenova.user.dto.UserSignupRequest;
import com.tradenova.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController    //JSON 기반 REST API를 만드는 컨트롤러라는 뜻
@RequiredArgsConstructor //final 필드를 자동으로 생성자 주입해줌
@RequestMapping("/api/auth") //모든 API는 /api/auth로 시작
public class UserController {

    private final UserService userService;

    /*
     * 회원가입
     * POST /api/auth/signup
     */
    @PostMapping("/signup")                                         //front 에서 받는 json body를 request dto로 받음
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody UserSignupRequest request) { //@Valid는 userSignupRequest DTO에 있는 검증 어노테이션을 체크 (@Email, @notBlank @Size 등등)
        UserResponse response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /*
     * 로그인
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) { //@Valid로 DTO 검증
        LoginResponse response = userService.login(request);  //받은 요청으로 userService에 로그인 메서드 실행 결과 response에 담기
        return ResponseEntity.ok(response); //응답
    }
}
