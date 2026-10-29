package com.tradenova.report.dto;

import java.util.Locale;

public enum AiReviewLanguage {
    EN("en"),
    KO("ko");

    private final String code;

    AiReviewLanguage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AiReviewLanguage resolve(String value) {
        if (value == null) return KO;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "en".equals(normalized) ? EN : KO;
    }
}
