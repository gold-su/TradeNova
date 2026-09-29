package com.tradenova.waitlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.waitlist.repository.BetaWaitlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:beta-waitlist;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "tradenova.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "openai.api-key=test",
        "kis.appkey=test",
        "kis.appsecret=test"
})
class BetaWaitlistIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BetaWaitlistRepository repository;

    @Test
    void unauthenticatedVisitorCanJoinWithAttribution() throws Exception {
        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("Visitor@Example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("visitor@example.com"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").exists());

        var saved = repository.findAll().get(0);
        assertThat(saved.getUtmSource()).isEqualTo("instagram");
        assertThat(saved.getUtmMedium()).isEqualTo("reels");
        assertThat(saved.getUtmCampaign()).isEqualTo("blind_chart");
        assertThat(saved.getUtmContent()).isEqualTo("reel_01");
        assertThat(saved.isSmsOptIn()).isFalse();
        assertThat(saved.getPhoneNumber()).isNull();
    }

    @Test
    void storesNormalizedPhoneNumberWhenSmsConsentIsEnabled() throws Exception {
        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("sms@example.com", true, "010-1234-5678")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());

        var saved = repository.findAll().get(0);
        assertThat(saved.isSmsOptIn()).isTrue();
        assertThat(saved.getPhoneNumber()).isEqualTo("01012345678");
    }

    @Test
    void rejectsSmsConsentWithoutPhoneNumber() throws Exception {
        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("missing-phone@example.com", true, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsInvalidPhoneNumber() throws Exception {
        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("invalid-phone@example.com", true, "011-123-4567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void discardsPhoneNumberWhenSmsConsentIsDisabled() throws Exception {
        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("opt-out@example.com", false, "010-1234-5678")))
                .andExpect(status().isCreated());

        var saved = repository.findAll().get(0);
        assertThat(saved.isSmsOptIn()).isFalse();
        assertThat(saved.getPhoneNumber()).isNull();
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","attribution":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.investmentExperience").exists())
                .andExpect(jsonPath("$.errors.primaryChallenge").exists())
                .andExpect(jsonPath("$.errors.participationIntent").exists());
    }

    @Test
    void rejectsDuplicateEmailWithoutOverwritingExistingEntry() throws Exception {
        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("same@example.com")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("SAME@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("WAITLIST_EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(repository.count()).isEqualTo(1);
    }

    private String validRequest(String email) throws Exception {
        return validRequest(email, false, null);
    }

    private String validRequest(String email, boolean smsOptIn, String phoneNumber) throws Exception {
        return mapper.writeValueAsString(new Request(
                email,
                "SIX_MONTHS_TO_TWO_YEARS",
                "PLAN_DISCIPLINE",
                "YES",
                smsOptIn,
                phoneNumber,
                new Attribution("instagram", "reels", "blind_chart", "reel_01")
        ));
    }

    record Request(String email, String investmentExperience, String primaryChallenge,
                   String participationIntent, boolean smsOptIn, String phoneNumber,
                   Attribution attribution) {}
    record Attribution(String utmSource, String utmMedium, String utmCampaign, String utmContent) {}
}
