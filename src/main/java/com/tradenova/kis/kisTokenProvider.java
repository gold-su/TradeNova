package com.tradenova.kis;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.kis.config.kisProperties;
import com.tradenova.kis.dto.KisTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Component //이 클래스를 Bean 으로 등록
@RequiredArgsConstructor //final 필드 생성자 자동 생성
public class kisTokenProvider {

    //KIS baseUrl 박힌 RestClient 객체
    private final RestClient kisRestClient;
    private final kisProperties props;
    private final KisApiDiagnostics diagnostics;

    private volatile String cachedToken ;
    private volatile Instant expiresAt; //만료 시각
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 토큰을 가져오되, 없거나 만료 임박이면 재발급한다.
     */
    public String getAccessToken() {
        //만료 60초 전이면 재발급(안전 마진)
        if(cachedToken != null && expiresAt != null && Instant.now().isBefore(expiresAt.minusSeconds(60))){
            return cachedToken;
        }

        lock.lock();
        try {
            //더블체크
            if(cachedToken != null && expiresAt != null && Instant.now().isBefore(expiresAt.minusSeconds(60))){
                return cachedToken;
            }
            //토큰 발급 엔드포인트
            String tokenPath = "/oauth2/tokenP";

            //Client Credentials Grant 형태로 발급 받는 경우가 일반적
            // grant_type=client_credentials
            // KIS 가 내려주는 토큰 응답을 JSON을 자바 객체로 받기 위함.
            KisTokenResponse res;
            try {
                res = kisRestClient.post()
                        .uri(tokenPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "grant_type", "client_credentials",
                                "appkey", props.appkey(),
                                "appsecret", props.appsecret()
                        ))
                        .retrieve()
                        .body(KisTokenResponse.class);
            } catch (RestClientResponseException exception) {
                diagnostics.logHttpFailure(KisApiDiagnostics.Stage.OAUTH_TOKEN, exception);
                throw new CustomException(ErrorCode.KIS_API_CALL_FAILED);
            } catch (RestClientException exception) {
                diagnostics.logTransportFailure(KisApiDiagnostics.Stage.OAUTH_TOKEN);
                throw new CustomException(ErrorCode.KIS_API_CALL_FAILED);
            }

            //응답이 null 이라면 예외 던지기
            if (res == null || res.accessToken() == null){
                diagnostics.logResponseFailure(
                        KisApiDiagnostics.Stage.OAUTH_TOKEN,
                        "INVALID_TOKEN_RESPONSE",
                        "KIS token response did not contain an access token"
                );
                throw new CustomException(ErrorCode.KIS_TOKEN_RESPONSE_INVALID);
            }

            cachedToken = res.accessToken();

            //expires_in이 "초"라고 가정(대부분 그렇게 옴)
            expiresAt = Instant.now().plusSeconds(res.expiresIn() > 0 ? res.expiresIn() : 60 * 60);
            return cachedToken;
        }finally {
            lock.unlock();
        }
    }

}
