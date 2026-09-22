package com.tradenova.training.dto;
import com.tradenova.training.entity.ChartDrawingType;
import java.math.BigDecimal; import java.time.LocalDate;
public record ChartDrawingCreateRequest(ChartDrawingType type, LocalDate startDate, BigDecimal startPrice, LocalDate endDate, BigDecimal endPrice) {}
