package com.tradenova.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** User-authored qualitative evidence attached by explicit id to a canonical trade. */
public record TradeActionAiEvidence(
        Long eventId,
        Long chartId,
        Long tradeId,
        String side,
        Long candleTime,
        BigDecimal qty,
        BigDecimal price,
        Instant createdAt,
        List<TradeReasonAiEvidence> reasons,
        EvidenceTimelineAnchor timeline,
        String reasonMode,
        Long scenarioSnapshotId,
        ScenarioPlanAiEvidence scenarioPlan,
        String planRelation,
        String changeReason
) {
    public TradeActionAiEvidence {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
    public TradeActionAiEvidence(Long eventId, Long chartId, Long tradeId, String side, Long candleTime,
            BigDecimal qty, BigDecimal price, Instant createdAt, List<TradeReasonAiEvidence> reasons,
            EvidenceTimelineAnchor timeline) {
        this(eventId, chartId, tradeId, side, candleTime, qty, price, createdAt, reasons, timeline,
                null, null, null, null, null);
    }

    public TradeActionAiEvidence(Long eventId, Long chartId, Long tradeId, String side, Long candleTime,
            BigDecimal qty, BigDecimal price, Instant createdAt, List<TradeReasonAiEvidence> reasons,
            EvidenceTimelineAnchor timeline, String reasonMode, Long scenarioSnapshotId,
            ScenarioPlanAiEvidence scenarioPlan) {
        this(eventId, chartId, tradeId, side, candleTime, qty, price, createdAt, reasons, timeline,
                reasonMode, scenarioSnapshotId, scenarioPlan, null, null);
    }
}
