package com.tradenova.report.service;

import com.tradenova.report.dto.*;
import org.springframework.stereotype.Component;

/** Compact prompt serialization of user-authored evidence only. */
@Component
public class SessionQualitativeEvidenceFormatter {
    public String format(SessionQualitativeEvidenceContext context) {
        if (context == null) return "qualitative evidence unavailable";
        StringBuilder out = new StringBuilder();
        for (ChartQualitativeEvidenceContext chart : context.charts()) {
            if (chart.snapshots().isEmpty() && chart.notes().isEmpty()) continue;
            out.append("- chartId=").append(chart.chartId()).append(", active=").append(chart.active())
                    .append(", refreshed=").append(chart.refreshed()).append('\n');
            for (SnapshotAiEvidence snapshot : chart.snapshots()) {
                out.append("  * SNAPSHOT#").append(snapshot.version())
                        .append(" authoredAt=").append(value(snapshot.authoredAt()))
                        .append(", timeline=").append(anchor(snapshot.timeline()))
                        .append(", text={thesis:").append(value(snapshot.thesis()))
                        .append(",entryReason:").append(value(snapshot.entryReason()))
                        .append(",exitPlan:").append(value(snapshot.exitPlan()))
                        .append(",riskNote:").append(value(snapshot.riskNote()))
                        .append(",freeNote:").append(value(snapshot.freeNote())).append("}\n");
            }
            for (NoteAiEvidence note : chart.notes()) {
                out.append("  * NOTE#").append(note.eventId()).append(" authoredAt=").append(value(note.authoredAt()))
                        .append(", timeline=").append(anchor(note.timeline()))
                        .append(", summary=").append(value(note.summary()));
                if (note.detail() != null && !note.detail().equals(note.summary())) {
                    out.append(", detail=").append(note.detail());
                }
                out.append('\n');
            }
        }
        return out.isEmpty() ? "user-authored snapshot/note 없음" : out.toString();
    }

    private String anchor(EvidenceTimelineAnchor anchor) {
        if (anchor == null || "UNRESOLVED".equals(anchor.resolution())) return "UNRESOLVED";
        return "{" + anchor.resolution() + ",progress=" + value(anchor.progressIndex())
                + ",candleTime=" + value(anchor.candleTime()) + ",tradeId=" + value(anchor.tradeId())
                + ",episode=" + value(anchor.episodeReference())
                + ",riskHistoryId=" + value(anchor.riskRuleHistoryId()) + "}";
    }

    private String value(Object value) {
        return value == null ? "undefined" : value.toString().replace('\n', ' ').trim();
    }
}
