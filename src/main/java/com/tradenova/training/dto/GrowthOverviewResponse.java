package com.tradenova.training.dto;

import java.util.List;

public record GrowthOverviewResponse(
        String period,
        long totalCompletedSessions,
        long totalTrades,
        long totalXp,
        int level,
        String levelTitle,
        long currentLevelXp,
        long nextLevelXp,
        double progressPercent,
        GrowthMetricResponse planSessionRate,
        GrowthMetricResponse actionReasonRate,
        GrowthMetricResponse riskRuleSessionRate,
        GrowthMetricResponse aiReviewSessionRate,
        Double averageSessionAiScore,
        List<GrowthTrendPointResponse> scoreTrend
) {}
