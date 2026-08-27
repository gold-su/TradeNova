package com.tradenova.report.dto;

import java.util.List;

public record SessionQualitativeEvidenceContext(
        Long sessionId,
        List<ChartQualitativeEvidenceContext> charts
) {
    public SessionQualitativeEvidenceContext {
        charts = List.copyOf(charts);
    }
}
