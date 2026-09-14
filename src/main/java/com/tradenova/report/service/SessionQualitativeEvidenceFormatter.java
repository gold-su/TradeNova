package com.tradenova.report.service;

import com.tradenova.report.dto.*;
import org.springframework.stereotype.Component;

/** Keeps deterministic execution facts distinct from user-authored evidence. */
@Component
public class SessionQualitativeEvidenceFormatter {
    public String format(SessionQualitativeEvidenceContext context) {
        if (context == null) return "qualitative evidence unavailable";
        StringBuilder out = new StringBuilder();
        out.append("NEUTRAL METADATA: tradedChartCount, totalChartCount, completedChartCount and no-trade chart count "
                + "are descriptive context only, never positive/negative quality signals or grounds for strengths, warnings, "
                + "recommendations or nextTrainingFocus. 1 of 4 charts traded does not establish beneficial focus, "
                + "learning, discipline, insufficient exploration or poor diversification.\n");
        out.append("[Backend Risk Plan Compliance / deterministic execution facts, not user intent]\n");
        for (RiskComplianceAiEvidence risk : context.riskCompliance()) {
            out.append("  RISK PLAN COMPLIANCE: ").append(risk).append('\n');
        }
        out.append("Compliance describes only the linked trigger/execution, not overall plan quality. "
                + "FOLLOWED remains FOLLOWED with negative PnL. UNKNOWN is insufficient evidence, not NOT_FOLLOWED. "
                + "A null triggeredReason means no confirmed trigger reason, not proof that no trigger occurred. "
                + "plannedExitQty includes integer floor/minimum-one-share rules; executedExitPercent is the actual position fraction.\n");
        out.append("[User-authored Qualitative Evidence]\n");
        for (ChartQualitativeEvidenceContext chart : context.charts()) {
            if (chart.snapshots().isEmpty() && chart.notes().isEmpty() && chart.tradeActions().isEmpty()) continue;
            out.append("- chartId=").append(chart.chartId()).append(", active=").append(chart.active())
                    .append(", refreshed=").append(chart.refreshed()).append('\n');
            for (SnapshotAiEvidence snapshot : chart.snapshots()) {
                out.append("  * GENERIC SNAPSHOT CONTEXT / SNAPSHOT#").append(snapshot.version())
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
            for (TradeActionAiEvidence action : chart.tradeActions()) {
                out.append("  * ACTION-TIME TRADE REASON event#").append(action.eventId())
                        .append(", tradeId=").append(action.tradeId()).append(", side=").append(action.side())
                        .append(", candleTime=").append(value(action.candleTime()))
                        .append(", qty=").append(value(action.qty())).append(", price=").append(value(action.price()))
                        .append(", authoredAt=").append(value(action.createdAt()))
                        .append(", timeline=").append(anchor(action.timeline())).append(", reasons=")
                        .append(action.reasons()).append('\n');
                if (action.scenarioPlan() != null) out.append("    LINKED PRE-TRADE PLAN snapshot#")
                        .append(action.scenarioPlan().snapshotId()).append(" text={entryReason:")
                        .append(value(action.scenarioPlan().entryReason())).append(",thesis:")
                        .append(value(action.scenarioPlan().thesis())).append("}\n");
            }
        }
        return out.isEmpty() ? "user-authored snapshot/note/trade reason 없음" : out.toString();
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
