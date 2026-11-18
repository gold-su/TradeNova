package com.tradenova.kis;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.kis.config.kisProperties;
import com.tradenova.kis.service.KisMarketDataService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KisApiDiagnosticsTest {

    @Test
    void logsOnlyAllowlistedAndRedactedHttpErrorFields() {
        KisApiDiagnostics diagnostics = new KisApiDiagnostics(new ObjectMapper());
        Logger logger = (Logger) LoggerFactory.getLogger(KisApiDiagnostics.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String body = """
                    {
                      "msg_cd":"EGW00123",
                      "msg1":"invalid appkey visible-secret Bearer token-value",
                      "appsecret":"must-not-appear",
                      "access_token":"must-not-appear-either"
                    }
                    """;
            RestClientResponseException exception = HttpClientErrorException.create(
                    HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY,
                    body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

            diagnostics.logHttpFailure(KisApiDiagnostics.Stage.OAUTH_TOKEN, exception);

            String logged = appender.list.get(0).getFormattedMessage();
            assertThat(logged)
                    .contains("stage=oauth_token", "httpStatus=401", "errorCode=EGW00123")
                    .contains("[REDACTED]")
                    .doesNotContain("visible-secret", "token-value", "must-not-appear", "access_token", body);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void oauthHttpFailureIsClassifiedBeforeItBecomesCommonException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kis.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiDiagnostics diagnostics = mock(KisApiDiagnostics.class);
        kisTokenProvider provider = new kisTokenProvider(builder.build(), properties(), diagnostics);
        server.expect(once(), requestTo("https://kis.example.test/oauth2/tokenP"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error_code\":\"EGW00123\",\"error_description\":\"invalid credentials\"}"));

        assertThatThrownBy(provider::getAccessToken)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KIS_API_CALL_FAILED);

        verify(diagnostics).logHttpFailure(
                org.mockito.ArgumentMatchers.eq(KisApiDiagnostics.Stage.OAUTH_TOKEN),
                any(RestClientResponseException.class));
        server.verify();
    }

    @Test
    void marketDataHttpFailureIsClassifiedSeparatelyFromOauth() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kis.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiDiagnostics diagnostics = mock(KisApiDiagnostics.class);
        kisTokenProvider provider = mock(kisTokenProvider.class);
        when(provider.getAccessToken()).thenReturn("test-token");
        KisMarketDataService service = new KisMarketDataService(
                builder.build(), properties(), provider, diagnostics);
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString(
                        "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg_cd\":\"EGW00201\",\"msg1\":\"request limit exceeded\"}"));

        assertThatThrownBy(() -> service.getCandles("J", "000100", "20251119", "20261118", "D", "0"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KIS_API_CALL_FAILED);

        verify(diagnostics).logHttpFailure(
                org.mockito.ArgumentMatchers.eq(KisApiDiagnostics.Stage.MARKET_DATA),
                any(RestClientResponseException.class));
        server.verify();
    }

    @Test
    void kisLogicalErrorIsReportedBeforeMissingCandleData() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://kis.example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiDiagnostics diagnostics = mock(KisApiDiagnostics.class);
        kisTokenProvider provider = mock(kisTokenProvider.class);
        when(provider.getAccessToken()).thenReturn("test-token");
        KisMarketDataService service = new KisMarketDataService(
                builder.build(), properties(), provider, diagnostics);
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString(
                        "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"rt_cd\":\"1\",\"msg_cd\":\"EGW00202\",\"msg1\":\"invalid date range\",\"output2\":null}"));

        assertThatThrownBy(() -> service.getCandles("J", "000100", "20251119", "20261118", "D", "0"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KIS_API_ERROR);

        verify(diagnostics).logResponseFailure(
                KisApiDiagnostics.Stage.MARKET_DATA, "EGW00202", "invalid date range");
        server.verify();
    }

    private kisProperties properties() {
        return new kisProperties("https://kis.example.test", "app-key", "app-secret", "P");
    }
}
