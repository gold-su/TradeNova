package com.tradenova.beta;

import com.tradenova.security.JwtTokenProvider;
import com.tradenova.user.entity.User;
import com.tradenova.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:public-beta-gate;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "tradenova.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "openai.api-key=test",
        "kis.appkey=test",
        "kis.appsecret=test",
        "app.beta.public-enabled=false",
        "app.beta.allowed-emails= Internal@Example.com "
})
class PublicBetaGateIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private User regularUser;
    private User internalUser;

    @BeforeEach
    void setUp() {
        regularUser = users.save(User.createLocalUser(
                "visitor@example.com", passwordEncoder.encode("Password1"), "visitor"));
        internalUser = users.save(User.createLocalUser(
                "internal@example.com", passwordEncoder.encode("Password1"), "internal"));
    }

    @Test
    void blocksSignupWhilePrivateBetaIsClosed() throws Exception {
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","nickname":"new-user","password":"Password1"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PUBLIC_BETA_CLOSED"));
    }

    @Test
    void blocksProtectedMutationForRegularUser() throws Exception {
        mvc.perform(post("/api/paper-accounts")
                        .header("Authorization", bearer(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"blocked","initialBalance":10000000}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PUBLIC_BETA_CLOSED"));
    }

    @Test
    void blocksExistingTrainingApiForRegularUser() throws Exception {
        mvc.perform(get("/api/training/sessions/active")
                        .header("Authorization", bearer(regularUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PUBLIC_BETA_CLOSED"));
    }

    @Test
    void allowsConfiguredInternalUserToCreatePaperAccount() throws Exception {
        mvc.perform(post("/api/paper-accounts")
                        .header("Authorization", bearer(internalUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"internal qa","initialBalance":10000000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("internal qa"));
    }

    @Test
    void reportsAccessWithoutExposingAllowlist() throws Exception {
        mvc.perform(get("/api/beta/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicBetaEnabled").value(false))
                .andExpect(jsonPath("$.accessAllowed").value(false));

        mvc.perform(get("/api/beta/access")
                        .header("Authorization", bearer(internalUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicBetaEnabled").value(false))
                .andExpect(jsonPath("$.accessAllowed").value(true));
    }

    @Test
    void waitlistRemainsAvailableWhilePrivateBetaIsClosed() throws Exception {
        mvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"waitlist@example.com",
                                  "investmentExperience":"SIX_MONTHS_TO_TWO_YEARS",
                                  "primaryChallenge":"PLAN_DISCIPLINE",
                                  "participationIntent":"YES",
                                  "smsOptIn":false,
                                  "phoneNumber":null,
                                  "attribution":{}
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void healthRemainsAvailableWhilePrivateBetaIsClosed() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void loginRemainsAvailableForExistingUsers() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"visitor@example.com","password":"Password1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user);
    }
}
