package com.tradenova.training.dto;
import com.tradenova.training.entity.*; import java.math.BigDecimal; import java.time.*;
public record ChartDrawingResponse(Long id, Long chartId, ChartDrawingType type, LocalDate startDate, BigDecimal startPrice, LocalDate endDate, BigDecimal endPrice, OffsetDateTime createdAt) { public static ChartDrawingResponse from(ChartDrawing d){return new ChartDrawingResponse(d.getId(),d.getChart().getId(),d.getType(),d.getStartDate(),d.getStartPrice(),d.getEndDate(),d.getEndPrice(),d.getCreatedAt());}}
