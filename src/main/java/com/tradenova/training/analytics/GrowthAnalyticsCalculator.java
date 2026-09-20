package com.tradenova.training.analytics;

import com.tradenova.training.dto.GrowthMetricResponse;
import com.tradenova.training.dto.GrowthOverviewResponse;
import com.tradenova.training.dto.GrowthLifetimeResponse;
import com.tradenova.training.dto.GrowthPeriodResponse;
import com.tradenova.training.dto.GrowthTrendPointResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class GrowthAnalyticsCalculator {
    public static final int XP_SESSION_COMPLETE = 100;
    public static final int XP_PLAN_SESSION = 20;
    public static final int XP_REASONED_USER_TRADE = 10;
    public static final int XP_RISK_RULE_SESSION = 20;
    public static final int XP_SESSION_AI_REVIEW = 20;
    public static final int XP_PER_LEVEL = 500;

    public GrowthOverviewResponse calculate(String periodKey, Integer limit,
                                            List<SessionFact> lifetimeSessions,
                                            List<SessionFact> periodSessions) {
        long lifetimePlans = lifetimeSessions.stream().filter(SessionFact::hasPlan).count();
        long lifetimeReasons = lifetimeSessions.stream().mapToLong(SessionFact::reasonedUserTradeCount).sum();
        long lifetimeRisks = lifetimeSessions.stream().filter(SessionFact::hasRiskRule).count();
        long lifetimeAi = lifetimeSessions.stream().filter(f -> f.aiScore() != null).count();
        long xp = lifetimeSessions.size() * XP_SESSION_COMPLETE
                + lifetimePlans * XP_PLAN_SESSION
                + lifetimeReasons * XP_REASONED_USER_TRADE
                + lifetimeRisks * XP_RISK_RULE_SESSION
                + lifetimeAi * XP_SESSION_AI_REVIEW;
        int level = Math.toIntExact(xp / XP_PER_LEVEL) + 1;
        long currentLevelXp = xp % XP_PER_LEVEL;

        long trades = periodSessions.stream().mapToLong(SessionFact::tradeCount).sum();
        long planSessions = periodSessions.stream().filter(SessionFact::hasPlan).count();
        long userTrades = periodSessions.stream().mapToLong(SessionFact::userTradeCount).sum();
        long reasonedTrades = periodSessions.stream().mapToLong(SessionFact::reasonedUserTradeCount).sum();
        long riskSessions = periodSessions.stream().filter(SessionFact::hasRiskRule).count();
        long aiSessions = periodSessions.stream().filter(f -> f.aiScore() != null).count();
        List<GrowthTrendPointResponse> trend = periodSessions.stream()
                .filter(f -> f.aiScore() != null)
                .sorted(Comparator.comparing(SessionFact::completedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(f -> new GrowthTrendPointResponse(f.sessionId(), f.completedAt(), f.aiScore()))
                .toList();
        Double average = trend.isEmpty() ? null : trend.stream().mapToInt(GrowthTrendPointResponse::score).average().orElseThrow();
        GrowthLifetimeResponse lifetime = new GrowthLifetimeResponse(xp, level, title(level), currentLevelXp,
                XP_PER_LEVEL - currentLevelXp, currentLevelXp * 100.0 / XP_PER_LEVEL);
        GrowthPeriodResponse period = new GrowthPeriodResponse(periodKey, limit, periodSessions.size(), trades,
                metric(planSessions, periodSessions.size()), metric(reasonedTrades, userTrades),
                metric(riskSessions, periodSessions.size()), metric(aiSessions, periodSessions.size()), average, trend);
        return new GrowthOverviewResponse(lifetime, period);
    }

    private GrowthMetricResponse metric(long numerator, long denominator) {
        return new GrowthMetricResponse(numerator, denominator, denominator == 0 ? 0 : numerator * 100.0 / denominator);
    }

    private String title(int level) {
        if (level >= 8) return "Consistent Operator";
        if (level >= 5) return "Process Builder";
        if (level >= 3) return "Disciplined Trader";
        if (level >= 2) return "Planner";
        return "Observer";
    }

    public int levelForXp(long xp) {
        return Math.toIntExact(xp / XP_PER_LEVEL) + 1;
    }

    public record SessionFact(Long sessionId, Instant completedAt, long tradeCount,
                              long userTradeCount, long reasonedUserTradeCount,
                              boolean hasPlan, boolean hasRiskRule, Integer aiScore) {}
}
