package com.tradenova.community.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradenova.community.dto.CommunityDtos.Author;
import com.tradenova.report.entity.*;
import com.tradenova.report.repository.*;
import com.tradenova.training.analytics.GrowthAnalyticsCalculator;
import com.tradenova.training.entity.*;
import com.tradenova.training.repository.*;
import com.tradenova.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CommunityProfileService {
    private final TrainingSessionRepository sessions;
    private final TrainingSessionChartRepository charts;
    private final ReportDocumentRepository documents;
    private final TrainingEventRepository events;
    private final TrainingRiskRuleHistoryRepository risks;
    private final GrowthAnalyticsCalculator calculator;

    @Transactional(readOnly = true)
    public Map<Long, Author> profiles(Collection<User> users) {
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, Function.identity(), (a,b)->a));
        if (userMap.isEmpty()) return Map.of();
        List<TrainingSession> completed = sessions.findAllForCommunityProfiles(userMap.keySet(), TrainingStatus.COMPLETED);
        Map<Long, Long> completedCounts = completed.stream().collect(Collectors.groupingBy(s -> s.getUser().getId(), Collectors.counting()));
        List<Long> sessionIds = completed.stream().map(TrainingSession::getId).toList();
        List<TrainingSessionChart> activeCharts = sessionIds.isEmpty() ? List.of() : charts.findHistoryChartsBySessionIds(sessionIds);
        Map<Long, Long> chartUser = activeCharts.stream().collect(Collectors.toMap(TrainingSessionChart::getId, c -> c.getSession().getUser().getId()));
        Map<Long, Long> chartSession = activeCharts.stream().collect(Collectors.toMap(TrainingSessionChart::getId, c -> c.getSession().getId()));
        List<Long> chartIds = new ArrayList<>(chartUser.keySet());
        Set<Long> planSessions = new HashSet<>();
        if (!chartIds.isEmpty()) for (ReportDocument d : documents.findAllByUserIdInAndChartIdInAndKind(userMap.keySet(), chartIds, ReportKind.SNAPSHOT)) {
            JsonNode tags = d.getContentJson() == null ? null : d.getContentJson().get("tags");
            if (tags != null && tags.isArray()) for (JsonNode tag : tags) if ("SCENARIO".equals(tag.asText())) planSessions.add(chartSession.get(d.getChartId()));
        }
        Set<Long> riskSessions = sessionIds.isEmpty() ? Set.of() : risks.findAllByUserIdInAndSessionIdIn(userMap.keySet(), sessionIds).stream().map(TrainingRiskRuleHistory::getSessionId).collect(Collectors.toSet());
        Map<Long, Long> reasoned = new HashMap<>();
        Set<Long> aiSessions = new HashSet<>();
        if (!chartIds.isEmpty()) for (TrainingEvent e : events.findAllByUserIdInAndChartIdInOrderByIdAsc(userMap.keySet(), chartIds)) {
            Long sessionId = chartSession.get(e.getChartId());
            Long userId = chartUser.get(e.getChartId());
            JsonNode payload = e.getPayloadJson();
            if (e.getType() == Type.TRADE && e.getOrigin() == EventOrigin.USER && payload != null && payload.path("reasons").isArray() && !payload.path("reasons").isEmpty()) reasoned.merge(userId, 1L, Long::sum);
            if (e.getType() == Type.AI && e.getOrigin() == EventOrigin.SYSTEM && "SESSION".equals(payload == null ? null : payload.path("analysisScope").asText(null))) aiSessions.add(sessionId);
        }
        Map<Long, Long> plansByUser = planSessions.stream().map(id -> completed.stream().filter(s -> s.getId().equals(id)).findFirst().orElseThrow().getUser().getId()).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<Long, Long> risksByUser = riskSessions.stream().map(id -> completed.stream().filter(s -> s.getId().equals(id)).findFirst().orElseThrow().getUser().getId()).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<Long, Long> aiByUser = aiSessions.stream().map(id -> completed.stream().filter(s -> s.getId().equals(id)).findFirst().orElseThrow().getUser().getId()).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<Long, Author> result = new HashMap<>();
        userMap.forEach((id, user) -> {
            long count = completedCounts.getOrDefault(id, 0L);
            long xp = count * GrowthAnalyticsCalculator.XP_SESSION_COMPLETE
                    + plansByUser.getOrDefault(id, 0L) * GrowthAnalyticsCalculator.XP_PLAN_SESSION
                    + reasoned.getOrDefault(id, 0L) * GrowthAnalyticsCalculator.XP_REASONED_USER_TRADE
                    + risksByUser.getOrDefault(id, 0L) * GrowthAnalyticsCalculator.XP_RISK_RULE_SESSION
                    + aiByUser.getOrDefault(id, 0L) * GrowthAnalyticsCalculator.XP_SESSION_AI_REVIEW;
            result.put(id, new Author(id, user.getNickname(), calculator.levelForXp(xp), count));
        });
        return result;
    }
}
