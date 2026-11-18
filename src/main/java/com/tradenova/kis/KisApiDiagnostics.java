package com.tradenova.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisApiDiagnostics {

    private static final String UNKNOWN = "UNKNOWN";
    private static final int MAX_MESSAGE_LENGTH = 200;
    private static final List<String> ERROR_CODE_FIELDS = List.of("msg_cd", "error_code", "rt_cd");
    private static final List<String> MESSAGE_FIELDS = List.of("msg1", "error_description", "message");
    private static final Pattern LABELED_SECRET = Pattern.compile(
            "(?i)((?:appkey|appsecret|access_token|authorization|token)(?:\\s*[=:]\\s*|\\s+))[^\\s,;]+"
    );
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*");
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ObjectMapper objectMapper;

    public void logHttpFailure(Stage stage, RestClientResponseException exception) {
        SafeError error = parseAllowedFields(exception.getResponseBodyAsString());
        log.warn("KIS request failed: stage={}, httpStatus={}, errorCode={}, message={}",
                stage.value, exception.getStatusCode().value(), error.code(), error.message());
    }

    public void logTransportFailure(Stage stage) {
        log.warn("KIS request failed: stage={}, httpStatus=unavailable, errorCode=TRANSPORT_ERROR, "
                + "message=KIS transport request failed", stage.value);
    }

    public void logResponseFailure(Stage stage, String errorCode, String message) {
        log.warn("KIS request failed: stage={}, httpStatus=200, errorCode={}, message={}",
                stage.value, safeCode(errorCode), safeMessage(message));
    }

    SafeError parseAllowedFields(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new SafeError(UNKNOWN, "KIS request failed");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return new SafeError(
                    safeCode(firstText(root, ERROR_CODE_FIELDS)),
                    safeMessage(firstText(root, MESSAGE_FIELDS))
            );
        } catch (Exception ignored) {
            return new SafeError(UNKNOWN, "KIS request failed");
        }
    }

    private String firstText(JsonNode root, List<String> fieldNames) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = root.get(fieldName);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private String safeCode(String value) {
        if (value == null || !SAFE_ERROR_CODE.matcher(value).matches()) {
            return UNKNOWN;
        }
        return value;
    }

    private String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "KIS request failed";
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').trim();
        String redacted = LABELED_SECRET.matcher(singleLine).replaceAll("$1[REDACTED]");
        redacted = BEARER_TOKEN.matcher(redacted).replaceAll("$1[REDACTED]");
        return redacted.substring(0, Math.min(redacted.length(), MAX_MESSAGE_LENGTH));
    }

    public enum Stage {
        OAUTH_TOKEN("oauth_token"),
        MARKET_DATA("market_data");

        private final String value;

        Stage(String value) {
            this.value = value;
        }
    }

    record SafeError(String code, String message) {
    }
}
