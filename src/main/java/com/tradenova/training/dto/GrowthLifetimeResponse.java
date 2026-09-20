package com.tradenova.training.dto;

public record GrowthLifetimeResponse(
        long totalXp,
        int level,
        String levelTitle,
        long currentLevelXp,
        long nextLevelXp,
        double progressPercent
) {}
