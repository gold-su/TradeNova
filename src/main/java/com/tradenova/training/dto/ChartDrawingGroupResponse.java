package com.tradenova.training.dto;
import java.util.List;
public record ChartDrawingGroupResponse(Long chartId, List<ChartDrawingResponse> drawings) {}
