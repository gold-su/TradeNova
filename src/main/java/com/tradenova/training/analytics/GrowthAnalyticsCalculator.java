package com.tradenova.training.analytics;

import com.tradenova.training.dto.GrowthMetricResponse;
import com.tradenova.training.dto.GrowthOverviewResponse;
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

    public GrowthOverviewResponse calculate(String period, List<SessionFact> sessions) {
        long trades = sessions.stream().mapToLong(SessionFact::tradeCount).sum();
        long planSessions = sessions.stream().filter(SessionFact::hasPlan).count();
        long userTrades = sessions.stream().mapToLong(SessionFact::userTradeCount).sum();
        long reasonedTrades = sessions.stream().mapToLong(SessionFact::reasonedUserTradeCount).sum();
        long riskSessions = sessions.stream().filter(SessionFact::hasRiskRule).count();
        long aiSessions = sessions.stream().filter(f -> f.aiScore() != null).count();
        long xp = sessions.size() * XP_SESSION_COMPLETE
                + planSessions * XP_PLAN_SESSION
                + reasonedTrades * XP_REASONED_USER_TRADE
                + riskSessions * XP_RISK_RULE_SESSION
                + aiSessions * XP_SESSION_AI_REVIEW;
        int level = Math.toIntExact(xp / XP_PER_LEVEL) + 1;
        long currentLevelXp = xp % XP_PER_LEVEL;
        List<GrowthTrendPointResponse> trend = sessions.stream()
                .filter(f -> f.aiScore() != null)
                .sorted(Comparator.comparing(SessionFact::completedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(f -> new GrowthTrendPointResponse(f.sessionId(), f.completedAt(), f.aiScore()))
                .toList();
        Double average = trend.isEmpty() ? null : trend.stream().mapToInt(GrowthTrendPointResponse::score).average().orElseThrow();
        return new GrowthOverviewResponse(period, sessions.size(), trades, xp, level, title(level),
                currentLevelXp, XP_PER_LEVEL - currentLevelXp, currentLevelXp * 100.0 / XP_PER_LEVEL,
                metric(planSessions, sessions.size()), metric(reasonedTrades, userTrades),
                metric(riskSessions, sessions.size()), metric(aiSessions, sessions.size()), average, trend);
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

    public record SessionFact(Long sessionId, Instant completedAt, long tradeCount,
                              long userTradeCount, long reasonedUserTradeCount,
                              boolean hasPlan, boolean hasRiskRule, Integer aiScore) {}
}
