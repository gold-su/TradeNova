package com.tradenova.admin.contentstudio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.market.entity.MarketCandle;
import com.tradenova.market.repository.MarketCandleRepository;
import com.tradenova.paper.repository.PaperAccountRepository;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.security.JwtTokenProvider;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import com.tradenova.training.repository.TrainingSessionRepository;
import com.tradenova.user.entity.User;
import com.tradenova.user.entity.UserRole;
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

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:content-studio;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "tradenova.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "app.content-studio.case-key-secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "tradenova.jwt.access-token-validity-in-seconds=3600",
        "openai.api-key=test",
        "kis.appkey=test",
        "kis.appsecret=test",
        "app.beta.public-enabled=false"
})
class ContentStudioIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired SymbolRepository symbols;
    @Autowired MarketCandleRepository marketCandles;
    @Autowired PaperAccountRepository paperAccounts;
    @Autowired TrainingSessionRepository trainingSessions;
    @Autowired TrainingEventRepository trainingEvents;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private User admin;
    private User regularUser;

    @BeforeEach
    void setUp() {
        admin = User.createLocalUser("admin@example.com", passwordEncoder.encode("Password1"), "admin");
        admin.setRole(UserRole.ADMIN);
        admin = users.save(admin);
        regularUser = users.save(User.createLocalUser(
                "user@example.com", passwordEncoder.encode("Password1"), "user"));

        Symbol symbol = symbols.save(Symbol.builder()
                .market("KOSPI")
                .ticker("000001")
                .name("Internal test symbol")
                .currency("KRW")
                .active(true)
                .build());

        List<MarketCandle> candles = new ArrayList<>();
        LocalDate start = LocalDate.of(2020, 1, 2);
        for (int index = 0; index < 130; index++) {
            double close = 10_000 + index;
            candles.add(MarketCandle.builder()
                    .symbol(symbol)
                    .candleDate(start.plusDays(index))
                    .openPrice(close - 10)
                    .highPrice(close + 20)
                    .lowPrice(close - 20)
                    .closePrice(close)
                    .volume(100_000L + index)
                    .build());
        }
        marketCandles.saveAll(candles);
    }

    @Test
    void protectsAdminApiWithRoleBasedAuthentication() throws Exception {
        mvc.perform(get("/api/admin/content-studio/access"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/content-studio/access")
                        .header("Authorization", bearer(regularUser)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/content-studio/access")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        String adminCase = mvc.perform(post("/api/admin/content-studio/cases/random")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String caseKey = objectMapper.readTree(adminCase).path("caseKey").asText();

        mvc.perform(post("/api/admin/content-studio/cases/random")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/content-studio/cases/random")
                        .header("Authorization", bearer(regularUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/content-studio/cases/{caseKey}", caseKey))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/content-studio/cases/{caseKey}", caseKey)
                        .header("Authorization", bearer(regularUser)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/content-studio/cases/{caseKey}/reveal", caseKey))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/content-studio/cases/{caseKey}/reveal", caseKey)
                        .header("Authorization", bearer(regularUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void blindNextResetRevealAndCaseReloadAreExactAndReadOnly() throws Exception {
        long marketCount = marketCandles.count();
        long accountCount = paperAccounts.count();
        long sessionCount = trainingSessions.count();
        long eventCount = trainingEvents.count();

        String randomJson = mvc.perform(post("/api/admin/content-studio/cases/random")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seed":17,"analysisBars":80,"futureBars":40}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candles.length()").value(80))
                .andExpect(jsonPath("$.visibleThroughIndex").value(79))
                .andExpect(jsonPath("$.remainingBars").value(40))
                .andExpect(jsonPath("$.candles[0].date").doesNotExist())
                .andExpect(jsonPath("$.ticker").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode random = objectMapper.readTree(randomJson);
        String caseKey = random.path("caseKey").asText();
        assertThat(caseKey).isNotBlank();

        String sameSeedJson = mvc.perform(post("/api/admin/content-studio/cases/random")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"seed":17,"analysisBars":80,"futureBars":40}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode sameSeed = objectMapper.readTree(sameSeedJson);
        assertThat(sameSeed.path("candles")).isEqualTo(random.path("candles"));

        byte[] encryptedKey = Base64.getUrlDecoder().decode(caseKey);
        assertThat(encryptedKey).hasSizeGreaterThan(25);
        assertThat(ByteBuffer.wrap(encryptedKey, 1, Long.BYTES).getLong())
                .isNotEqualTo(symbols.findAll().get(0).getId());

        String nextJson = mvc.perform(get("/api/admin/content-studio/cases/{caseKey}", caseKey)
                        .param("throughIndex", "84")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candles.length()").value(85))
                .andExpect(jsonPath("$.remainingBars").value(35))
                .andExpect(jsonPath("$.candles[84].idx").value(84))
                .andReturn().getResponse().getContentAsString();

        String resetJson = mvc.perform(get("/api/admin/content-studio/cases/{caseKey}", caseKey)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candles.length()").value(80))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(resetJson).path("candles"))
                .isEqualTo(random.path("candles"));

        String repeatedNextJson = mvc.perform(get("/api/admin/content-studio/cases/{caseKey}", caseKey)
                        .param("throughIndex", "84")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(repeatedNextJson)).isEqualTo(objectMapper.readTree(nextJson));

        mvc.perform(get("/api/admin/content-studio/cases/{caseKey}", tamper(caseKey))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONTENT_STUDIO_CASE_INVALID"));

        String legacyCaseKey = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(25)
                .put((byte) 1)
                .putLong(symbols.findAll().get(0).getId())
                .putLong(marketCandles.findAll().get(0).getId())
                .putInt(80)
                .putInt(40)
                .array());
        mvc.perform(get("/api/admin/content-studio/cases/{caseKey}", legacyCaseKey)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONTENT_STUDIO_CASE_INVALID"));

        mvc.perform(get("/api/admin/content-studio/cases/{caseKey}/reveal", caseKey)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("000001"))
                .andExpect(jsonPath("$.name").value("Internal test symbol"))
                .andExpect(jsonPath("$.startDate").isNotEmpty())
                .andExpect(jsonPath("$.blindThroughDate").isNotEmpty())
                .andExpect(jsonPath("$.endDate").isNotEmpty())
                .andExpect(jsonPath("$.candles.length()").value(120))
                .andExpect(jsonPath("$.candles[0].date").isNotEmpty());

        assertThat(marketCandles.count()).isEqualTo(marketCount);
        assertThat(paperAccounts.count()).isEqualTo(accountCount);
        assertThat(trainingSessions.count()).isEqualTo(sessionCount);
        assertThat(trainingEvents.count()).isEqualTo(eventCount);
    }

    @Test
    void reportsInsufficientStoredDataWithoutFetchingOrPersisting() throws Exception {
        long marketCount = marketCandles.count();

        mvc.perform(post("/api/admin/content-studio/cases/random")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"analysisBars":240,"futureBars":120}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("CONTENT_STUDIO_DATA_INSUFFICIENT"));

        assertThat(marketCandles.count()).isEqualTo(marketCount);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user);
    }

    private String tamper(String caseKey) {
        char[] characters = caseKey.toCharArray();
        int index = characters.length / 2;
        characters[index] = characters[index] == 'A' ? 'B' : 'A';
        return new String(characters);
    }
}
