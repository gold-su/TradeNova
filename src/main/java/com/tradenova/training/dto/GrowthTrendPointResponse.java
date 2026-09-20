package com.tradenova.training.dto;

import java.time.Instant;

public record GrowthTrendPointResponse(Long sessionId, Instant completedAt, int score) {}
