package com.tradenova.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradenova.paper.dto.BaseCurrency;
import com.tradenova.paper.entity.PaperAccount;
import com.tradenova.symbol.dto.SymbolSector;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.ChartDrawingRepository;
import com.tradenova.user.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:drawing-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class ChartDrawingPersistenceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired ChartDrawingRepository drawings;

    private User owner;
    private User other;
    private TrainingSession session;
    private TrainingSessionChart chartA;
    private TrainingSessionChart chartB;

    @BeforeEach
    void setUp() {
        owner = persistUser("drawing-owner@example.com");
        other = persistUser("drawing-other@example.com");
        PaperAccount account = persistAccount(owner);
        session = TrainingSession.builder().user(owner).account(account).mode(TrainingMode.RANDOM)
                .status(TrainingStatus.IN_PROGRESS).build();
        em.persist(session);
        chartA = persistChart(session, persistSymbol("DRAW-A"), 0);
        chartB = persistChart(session, persistSymbol("DRAW-B"), 1);
        em.flush();
    }

    @Test
    void postAndBothGetsRoundTripHorizontalTrendAndZoneThroughDatabaseAndJson() throws Exception {
        post(chartA.getId(), owner.getId(), """
                {"type":"HORIZONTAL_LINE","startDate":null,"startPrice":51800,"endDate":null,"endPrice":null}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("HORIZONTAL_LINE"))
                .andExpect(jsonPath("$.startPrice").isNumber())
                .andExpect(jsonPath("$.startPrice").value(51800.0))
                .andExpect(jsonPath("$.startDate").doesNotExist())
                .andExpect(jsonPath("$.endDate").doesNotExist())
                .andExpect(jsonPath("$.endPrice").doesNotExist());

        MvcResult trendPost = post(chartA.getId(), owner.getId(), """
                {"type":"TREND_LINE","startDate":"2025-01-10","startPrice":51800,"endDate":"2025-01-20","endPrice":53500}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.chartId").value(chartA.getId()))
                .andExpect(jsonPath("$.type").value("TREND_LINE"))
                .andExpect(jsonPath("$.startDate").value("2025-01-10"))
                .andExpect(jsonPath("$.startPrice").isNumber())
                .andExpect(jsonPath("$.startPrice").value(51800.0))
                .andExpect(jsonPath("$.endDate").value("2025-01-20"))
                .andExpect(jsonPath("$.endPrice").isNumber())
                .andExpect(jsonPath("$.endPrice").value(53500.0)).andReturn();

        MvcResult zonePost = post(chartA.getId(), owner.getId(), """
                {"type":"ZONE","startDate":"2025-01-10","startPrice":51000,"endDate":"2025-01-20","endPrice":54000}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.chartId").value(chartA.getId()))
                .andExpect(jsonPath("$.type").value("ZONE"))
                .andExpect(jsonPath("$.startDate").value("2025-01-10"))
                .andExpect(jsonPath("$.startPrice").isNumber())
                .andExpect(jsonPath("$.startPrice").value(51000.0))
                .andExpect(jsonPath("$.endDate").value("2025-01-20"))
                .andExpect(jsonPath("$.endPrice").isNumber())
                .andExpect(jsonPath("$.endPrice").value(54000.0)).andReturn();

        long trendId = mapper.readTree(trendPost.getResponse().getContentAsString()).path("id").asLong();
        long zoneId = mapper.readTree(zonePost.getResponse().getContentAsString()).path("id").asLong();
        em.flush();
        em.clear();

        ChartDrawing storedTrend = drawings.findById(trendId).orElseThrow();
        assertThat(storedTrend.getStartDate()).isEqualTo(LocalDate.of(2025, 1, 10));
        assertThat(storedTrend.getEndDate()).isEqualTo(LocalDate.of(2025, 1, 20));
        assertThat(storedTrend.getStartPrice()).isEqualByComparingTo(new BigDecimal("51800.0000"));
        assertThat(storedTrend.getEndPrice()).isEqualByComparingTo(new BigDecimal("53500.0000"));

        mvc.perform(get("/api/training/charts/{chartId}/drawings", chartA.getId()).principal(auth(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].id").value(trendId))
                .andExpect(jsonPath("$[1].startDate").value("2025-01-10"))
                .andExpect(jsonPath("$[1].startPrice").value(51800.0))
                .andExpect(jsonPath("$[1].endDate").value("2025-01-20"))
                .andExpect(jsonPath("$[1].endPrice").value(53500.0))
                .andExpect(jsonPath("$[2].id").value(zoneId))
                .andExpect(jsonPath("$[2].startDate").value("2025-01-10"))
                .andExpect(jsonPath("$[2].endDate").value("2025-01-20"));

        mvc.perform(get("/api/training/sessions/{sessionId}/drawings", session.getId()).principal(auth(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chartId").value(chartA.getId()))
                .andExpect(jsonPath("$[0].drawings[1].id").value(trendId))
                .andExpect(jsonPath("$[0].drawings[1].startDate").value("2025-01-10"))
                .andExpect(jsonPath("$[0].drawings[1].endDate").value("2025-01-20"))
                .andExpect(jsonPath("$[0].drawings[2].id").value(zoneId));
    }

    @Test
    void postRoundTripsEveryV2DrawingType() throws Exception {
        post(chartA.getId(), owner.getId(), """
                {"type":"HORIZONTAL_LINE","startPrice":51800}
                """).andExpect(status().isCreated());
        post(chartA.getId(), owner.getId(), """
                {"type":"VERTICAL_LINE","startDate":"2025-01-10"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2025-01-10"))
                .andExpect(jsonPath("$.startPrice").doesNotExist());
        post(chartA.getId(), owner.getId(), """
                {"type":"TREND_LINE","startDate":"2025-01-10","startPrice":51800,"endDate":"2025-01-20","endPrice":53500}
                """).andExpect(status().isCreated());
        post(chartA.getId(), owner.getId(), """
                {"type":"RAY","startDate":"2025-01-10","startPrice":51800,"endDate":"2025-01-20","endPrice":53500}
                """).andExpect(status().isCreated());
        post(chartA.getId(), owner.getId(), """
                {"type":"ZONE","startDate":"2025-01-10","startPrice":51000,"endDate":"2025-01-20","endPrice":54000}
                """).andExpect(status().isCreated());
        post(chartA.getId(), owner.getId(), """
                {"type":"PARALLEL_CHANNEL","startDate":"2025-01-10","startPrice":51000,"endDate":"2025-01-20","endPrice":54000,"anchor3Date":"2025-01-15","anchor3Price":55000}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.anchor3Date").value("2025-01-15"))
                .andExpect(jsonPath("$.anchor3Price").value(55000.0));
        post(chartA.getId(), owner.getId(), """
                {"type":"FIBONACCI_RETRACEMENT","startDate":"2025-01-10","startPrice":51000,"endDate":"2025-01-20","endPrice":54000}
                """).andExpect(status().isCreated());
        post(chartA.getId(), owner.getId(), """
                {"type":"TEXT","startDate":"2025-01-10","startPrice":51800,"textContent":"support"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.textContent").value("support"));

        em.flush();
        assertThat(drawings.findAll()).extracting(ChartDrawing::getType)
                .containsExactlyInAnyOrder(ChartDrawingType.values());
    }

    @Test
    void rejectsOtherUsersChartGetPostAndDelete() throws Exception {
        JsonNode created = mapper.readTree(post(chartA.getId(), owner.getId(), """
                {"type":"TREND_LINE","startDate":"2025-01-10","startPrice":51800,"endDate":"2025-01-20","endPrice":53500}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long drawingId = created.path("id").asLong();

        mvc.perform(get("/api/training/charts/{chartId}/drawings", chartA.getId()).principal(auth(other.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TRAINING_CHART_NOT_FOUND"));
        post(chartA.getId(), other.getId(), """
                {"type":"ZONE","startDate":"2025-01-10","startPrice":51000,"endDate":"2025-01-20","endPrice":54000}
                """).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TRAINING_CHART_NOT_FOUND"));
        mvc.perform(delete("/api/training/charts/{chartId}/drawings/{drawingId}", chartA.getId(), drawingId)
                        .principal(auth(other.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TRAINING_CHART_NOT_FOUND"));
        assertThat(drawings.existsById(drawingId)).isTrue();
    }

    @Test
    void sessionBatchGroupsTrendAndZoneUnderTheirExactChartIds() throws Exception {
        post(chartA.getId(), owner.getId(), """
                {"type":"TREND_LINE","startDate":"2025-01-10","startPrice":51800,"endDate":"2025-01-20","endPrice":53500}
                """).andExpect(status().isCreated());
        post(chartB.getId(), owner.getId(), """
                {"type":"ZONE","startDate":"2025-01-10","startPrice":51000,"endDate":"2025-01-20","endPrice":54000}
                """).andExpect(status().isCreated());

        mvc.perform(get("/api/training/sessions/{sessionId}/drawings", session.getId()).principal(auth(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].chartId").value(chartA.getId()))
                .andExpect(jsonPath("$[0].drawings.length()").value(1))
                .andExpect(jsonPath("$[0].drawings[0].chartId").value(chartA.getId()))
                .andExpect(jsonPath("$[0].drawings[0].type").value("TREND_LINE"))
                .andExpect(jsonPath("$[1].chartId").value(chartB.getId()))
                .andExpect(jsonPath("$[1].drawings.length()").value(1))
                .andExpect(jsonPath("$[1].drawings[0].chartId").value(chartB.getId()))
                .andExpect(jsonPath("$[1].drawings[0].type").value("ZONE"));
    }

    @Test
    void refreshKeepsDrawingOnlyOnOldChartAndCompletedSessionRemainsReadable() throws Exception {
        post(chartA.getId(), owner.getId(), """
                {"type":"TREND_LINE","startDate":"2025-01-10","startPrice":51800,"endDate":"2025-01-20","endPrice":53500}
                """).andExpect(status().isCreated());
        chartA.deactivate();
        em.flush();
        TrainingSessionChart replacement = persistChart(session, persistSymbol("DRAW-C"), 0);
        replacement.markRefreshed();
        em.flush();

        mvc.perform(get("/api/training/charts/{chartId}/drawings", chartA.getId()).principal(auth(owner.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("TREND_LINE"));
        mvc.perform(get("/api/training/charts/{chartId}/drawings", replacement.getId()).principal(auth(owner.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        session.setStatus(TrainingStatus.COMPLETED);
        chartA.complete();
        replacement.complete();
        em.flush();
        mvc.perform(get("/api/training/charts/{chartId}/drawings", chartA.getId()).principal(auth(owner.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startDate").value("2025-01-10"))
                .andExpect(jsonPath("$[0].endDate").value("2025-01-20"));
    }

    private org.springframework.test.web.servlet.ResultActions post(Long chartId, Long userId, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/training/charts/{chartId}/drawings", chartId)
                .principal(auth(userId)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private TestingAuthenticationToken auth(Long userId) {
        return new TestingAuthenticationToken(userId, null, List.of());
    }

    private User persistUser(String email) {
        User user = User.createLocalUser(email, "hash", email.substring(0, email.indexOf('@')));
        em.persist(user);
        return user;
    }

    private PaperAccount persistAccount(User user) {
        PaperAccount account = PaperAccount.builder().user(user).name("Drawing Test")
                .initialBalance(new BigDecimal("1000000.00")).cashBalance(new BigDecimal("1000000.00"))
                .baseCurrency(BaseCurrency.KRW).isDefault(true).build();
        em.persist(account);
        return account;
    }

    private Symbol persistSymbol(String ticker) {
        Symbol symbol = Symbol.builder().market("KOSPI").ticker(ticker).name(ticker).currency("KRW")
                .active(true).trainingSector(SymbolSector.ETC).build();
        em.persist(symbol);
        return symbol;
    }

    private TrainingSessionChart persistChart(TrainingSession parent, Symbol symbol, int index) {
        TrainingSessionChart chart = TrainingSessionChart.builder().session(parent).chartIndex(index).symbol(symbol)
                .startDate(LocalDate.of(2024, 1, 1)).endDate(LocalDate.of(2024, 12, 31))
                .bars(100).analysisBars(60).trainingBars(40).hiddenFutureBars(0).progressIndex(59)
                .status(TrainingChartStatus.IN_PROGRESS).active(true).refreshed(false).build();
        em.persist(chart);
        return chart;
    }
}
