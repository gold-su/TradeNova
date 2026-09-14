package com.tradenova.report.dto;

import java.util.List;

public record SessionQualitativeEvidenceContext(
        Long sessionId,
        List<ChartQualitativeEvidenceContext> charts,
        List<RiskComplianceAiEvidence> riskCompliance
) {
    public SessionQualitativeEvidenceContext {
        charts = List.copyOf(charts);
        riskCompliance = List.copyOf(riskCompliance);
    }

    public SessionQualitativeEvidenceContext(Long sessionId, List<ChartQualitativeEvidenceContext> charts) {
        this(sessionId, charts, List.of());
    }
}
