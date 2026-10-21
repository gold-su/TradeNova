package com.tradenova.beta;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class BetaAccessPolicy {

    private final boolean publicBetaEnabled;
    private final Set<String> allowedEmails;

    public BetaAccessPolicy(
            @Value("${app.beta.public-enabled:true}") boolean publicBetaEnabled,
            @Value("${app.beta.allowed-emails:}") String allowedEmails
    ) {
        this.publicBetaEnabled = publicBetaEnabled;
        this.allowedEmails = parseAllowedEmails(allowedEmails);
        log.info("Public beta enabled: {}", publicBetaEnabled);
    }

    public boolean isPublicBetaEnabled() {
        return publicBetaEnabled;
    }

    public boolean isAllowedEmail(String email) {
        return email != null && allowedEmails.contains(normalize(email));
    }

    static Set<String> parseAllowedEmails(String value) {
        Set<String> emails = Arrays.stream(value == null ? new String[0] : value.split(","))
                .map(BetaAccessPolicy::normalize)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (emails.stream().anyMatch(email -> email.contains("*"))) {
            throw new IllegalArgumentException("APP_BETA_ALLOWED_EMAILS must not contain wildcards");
        }
        return emails;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
