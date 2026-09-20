package com.tradenova.training.dto;

import java.util.List;

public record GrowthPeriodResponse(
        String key,
        Integer limit,
        long completedSessions,
        long totalTrades,
        GrowthMetricResponse planSessionRate,
        GrowthMetricResponse actionReasonRate,
        GrowthMetricResponse riskRuleSessionRate,
        GrowthMetricResponse aiReviewSessionRate,
        Double averageSessionAiScore,
        List<GrowthTrendPointResponse> scoreTrend
) {}
