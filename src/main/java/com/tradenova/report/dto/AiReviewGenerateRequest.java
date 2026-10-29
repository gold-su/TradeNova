package com.tradenova.report.dto;

public record AiReviewGenerateRequest(String language) {
    public AiReviewLanguage resolvedLanguage() {
        return AiReviewLanguage.resolve(language);
    }
}
