[35msrc/main/java/com/tradenova/common/exception/ErrorCode.java[m[36m:[m[32m28[m[36m:[m    INVALID_[1;31mPASSWORD[m(HttpStatus.UNAUTHORIZED,"error.INVALID_[1;31mPASSWORD[m"),
[35msrc/main/java/com/tradenova/common/exception/GlobalExceptionHandler.java[m[36m:[m[32m48[m[36m:[m     * - 예: USER_NOT_FOUND, INVALID_[1;31mPASSWORD[m, EMAIL_NOT_VERIFIED 등
[35msrc/main/java/com/tradenova/security/JwtAuthenticationFilter.java[m[36m:[m[32m11[m[36m:[mimport org.springframework.security.authentication.Username[1;31mPassword[mAuthenticationToken;
[35msrc/main/java/com/tradenova/security/JwtAuthenticationFilter.java[m[36m:[m[32m45[m[36m:[m                 * new Username[1;31mPassword[mAuthenticationToken(principal, credentials, authorities)
[35msrc/main/java/com/tradenova/security/JwtAuthenticationFilter.java[m[36m:[m[32m51[m[36m:[m                var auth = new Username[1;31mPassword[mAuthenticationToken(
[35msrc/main/java/com/tradenova/security/JwtTokenProvider.java[m[36m:[m[32m9[m[36m:[mimport org.springframework.security.authentication.Username[1;31mPassword[mAuthenticationToken;
[35msrc/main/java/com/tradenova/security/JwtTokenProvider.java[m[36m:[m[32m57[m[36m:[m        return new Username[1;31mPassword[mAuthenticationToken(
[35msrc/main/java/com/tradenova/security/SecurityConfig.java[m[36m:[m[32m11[m[36m:[mimport org.springframework.security.crypto.bcrypt.BCrypt[1;31mPassword[mEncoder;
[35msrc/main/java/com/tradenova/security/SecurityConfig.java[m[36m:[m[32m12[m[36m:[mimport org.springframework.security.crypto.[1;31mpassword[m.[1;31mPassword[mEncoder;
[35msrc/main/java/com/tradenova/security/SecurityConfig.java[m[36m:[m[32m15[m[36m:[mimport org.springframework.security.web.authentication.Username[1;31mPassword[mAuthenticationFilter;
[35msrc/main/java/com/tradenova/security/SecurityConfig.java[m[36m:[m[32m30[m[36m:[m    public [1;31mPassword[mEncoder [1;31mpassword[mEncoder() {
[35msrc/main/java/com/tradenova/security/SecurityConfig.java[m[36m:[m[32m32[m[36m:[m        return new BCrypt[1;31mPassword[mEncoder();
[35msrc/main/java/com/tradenova/security/SecurityConfig.java[m[36m:[m[32m53[m[36m:[m                .addFilterBefore(jwtAuthenticationFilter, Username[1;31mPassword[mAuthenticationFilter.class) //모든 요청 앞단에서 JWT를 검사하겠다. 기본 인증 필터보다 먼저 실행해야 하므로 Before() 사용.
[35msrc/main/java/com/tradenova/user/dto/LoginRequest.java[m[36m:[m[32m16[m[36m:[m    @NotBlank(message = "{login.[1;31mpassword[m.notblank}")
[35msrc/main/java/com/tradenova/user/dto/LoginRequest.java[m[36m:[m[32m17[m[36m:[m    private String [1;31mpassword[m;
[35msrc/main/java/com/tradenova/user/dto/UserSignupRequest.java[m[36m:[m[32m22[m[36m:[m    @NotBlank(message = "{signup.[1;31mpassword[m.notblank}")
[35msrc/main/java/com/tradenova/user/dto/UserSignupRequest.java[m[36m:[m[32m23[m[36m:[m    @Size(min = 8, max = 64, message = "{signup.[1;31mpassword[m.size}")
[35msrc/main/java/com/tradenova/user/dto/UserSignupRequest.java[m[36m:[m[32m24[m[36m:[m    private String [1;31mpassword[m;
[35msrc/main/java/com/tradenova/user/entity/User.java[m[36m:[m[32m29[m[36m:[m    @Column(name = "[1;31mpassword[m_hash")
[35msrc/main/java/com/tradenova/user/entity/User.java[m[36m:[m[32m30[m[36m:[m    private String [1;31mpassword[mHash;
[35msrc/main/java/com/tradenova/user/entity/User.java[m[36m:[m[32m93[m[36m:[m                                       String encoded[1;31mPassword[m,
[35msrc/main/java/com/tradenova/user/entity/User.java[m[36m:[m[32m100[m[36m:[m                .[1;31mpassword[mHash(encoded[1;31mPassword[m)
[35msrc/main/java/com/tradenova/user/service/UserService.java[m[36m:[m[32m14[m[36m:[mimport org.springframework.security.crypto.[1;31mpassword[m.[1;31mPassword[mEncoder;
[35msrc/main/java/com/tradenova/user/service/UserService.java[m[36m:[m[32m34[m[36m:[m    private final [1;31mPassword[mEncoder [1;31mpassword[mEncoder; //스프링 시큐리티에서 제공. 비밀번호 암호화해서 저장할 때 사용.
[35msrc/main/java/com/tradenova/user/service/UserService.java[m[36m:[m[32m57[m[36m:[m        if(![1;31mpassword[mEncoder.matches(request.get[1;31mPassword[m(), user.get[1;31mPassword[mHash())){
[35msrc/main/java/com/tradenova/user/service/UserService.java[m[36m:[m[32m58[m[36m:[m            throw new CustomException(ErrorCode.INVALID_[1;31mPASSWORD[m);
[35msrc/main/java/com/tradenova/user/service/UserService.java[m[36m:[m[32m99[m[36m:[m        String encoded[1;31mPassword[m = [1;31mpassword[mEncoder.encode(request.get[1;31mPassword[m()); //평문 비밀번호 encode()로 암호화.
[35msrc/main/java/com/tradenova/user/service/UserService.java[m[36m:[m[32m104[m[36m:[m                encoded[1;31mPassword[m,
[35msrc/main/java/com/tradenova/user/service/UserService.java[m[36m:[m[32m112[m[36m:[m        return UserResponse.from(saved); //엔티티(User)를 그대로 클라이언트에게 보내지 않고 DTO로 변환해서 리턴. ([1;31mpassword[mHash 같은 민감 정보는 빼야 함, API 응답 형태를 엔티티 구조와 분리)
[35msrc/main/resources/ValidationMessages_en.properties[m[36m:[m[32m5[m[36m:[msignup.[1;31mpassword[m.notblank=Please enter your [1;31mpassword[m.
[35msrc/main/resources/ValidationMessages_en.properties[m[36m:[m[32m6[m[36m:[msignup.[1;31mpassword[m.size=[1;31mPassword[m must be at least 8 characters.
[35msrc/main/resources/ValidationMessages_ja.properties[m[36m:[m[32m5[m[36m:[msignup.[1;31mpassword[m.notblank=パスワードを入力してください。
[35msrc/main/resources/ValidationMessages_ja.properties[m[36m:[m[32m6[m[36m:[msignup.[1;31mpassword[m.size=パスワードは8文字以上にしてください。
[35msrc/main/resources/ValidationMessages_ko.properties[m[36m:[m[32m5[m[36m:[msignup.[1;31mpassword[m.notblank=비밀번호를 입력해주세요.
[35msrc/main/resources/ValidationMessages_ko.properties[m[36m:[m[32m6[m[36m:[msignup.[1;31mpassword[m.size=비밀번호는 8자 이상이어야 합니다.
[35msrc/main/resources/application-local.yml[m[36m:[m[32m5[m[36m:[m    [1;31mpassword[m: ${DB_[1;31mPASSWORD[m}
[35msrc/main/resources/application.yml[m[36m:[m[32m8[m[36m:[m    [1;31mpassword[m: ${DB_[1;31mPASSWORD[m}
[35msrc/main/resources/messages_en.properties[m[36m:[m[32m7[m[36m:[merror.INVALID_[1;31mPASSWORD[m=Incorrect [1;31mpassword[m.
[35msrc/main/resources/messages_ja.properties[m[36m:[m[32m7[m[36m:[merror.INVALID_[1;31mPASSWORD[m=パスワードが正しくありません。
[35msrc/main/resources/messages_ko.properties[m[36m:[m[32m7[m[36m:[merror.INVALID_[1;31mPASSWORD[m=비밀번호가 일치하지 않습니다.
[35mtarget/classes/application.yml[m[36m:[m[32m8[m[36m:[m    [1;31mpassword[m: ${DB_[1;31mPASSWORD[m}
