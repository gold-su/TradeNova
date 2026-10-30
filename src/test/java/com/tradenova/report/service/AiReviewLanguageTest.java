package com.tradenova.report.service;

import com.tradenova.report.dto.AiReviewGenerateRequest;
import com.tradenova.report.dto.AiReviewLanguage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiReviewLanguageTest {

    @Test
    void resolvesSupportedLanguagesAndFallsBackToKorean() {
        assertEquals(AiReviewLanguage.EN, new AiReviewGenerateRequest(" EN ").resolvedLanguage());
        assertEquals(AiReviewLanguage.KO, new AiReviewGenerateRequest("ko").resolvedLanguage());
        assertEquals(AiReviewLanguage.KO, new AiReviewGenerateRequest(null).resolvedLanguage());
        assertEquals(AiReviewLanguage.KO, new AiReviewGenerateRequest("invalid").resolvedLanguage());
    }
}
