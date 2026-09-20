package com.tradenova.training.dto;

public record GrowthOverviewResponse(
        GrowthLifetimeResponse lifetime,
        GrowthPeriodResponse period
) {}
