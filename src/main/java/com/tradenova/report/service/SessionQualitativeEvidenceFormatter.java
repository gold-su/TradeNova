package com.tradenova.report.service;

import com.tradenova.report.dto.*;
import org.springframework.stereotype.Component;

/** Keeps deterministic execution facts distinct from user-authored evidence. */
@Component
public class SessionQualitativeEvidenceFormatter {
    public String format(SessionQualitativeEvidenceContext context) {
        if (context == null) return "qualitative evidence unavailable";
        StringBuilder out = new StringBuilder();
        out.append("NO-TRADE EXCLUSION POLICY: charts with no trade and no explicit user-authored Scenario/Reason "
                + "are excluded from decision-quality evaluation. Missing no-trade reasons are not missing evidence, a warning, "
                + "or a training need. Never request a no-trade explanation or plan. Chart counts and trade coverage are omitted "
                + "because they are neither positive nor negative quality signals.\n");
        out.append("EVIDENCE INTERPRETATION CONTRACT: a pre-trade plan, an action-time user reason, an observed fact, "
                + "and an executed action are different scopes. A plan preceding an action does not prove causality. "
                + "Only an exact plan link establishes plan selection, and even that does not prove every condition was observed.\n");
        out.append("[확정된 리스크 실행 사실; 실행 범위에만 적용하며 사용자 의도를 뜻하지 않음]\n");
        for (RiskComplianceAiEvidence risk : context.riskCompliance()) {
            out.append("  - episode reference=").append(value(risk.episode()))
                    .append(", configured at trade={stop:").append(value(risk.stopLossConfigured()))
                    .append(", target:").append(value(risk.takeProfitConfigured()))
                    .append(", automatic exit:").append(value(risk.autoExitEnabledAtTrade())).append("}")
                    .append(", observed execution={reason:").append(executionReasonMeaning(risk.executionReason()))
                    .append(", automatic:").append(value(risk.actualExitAutomatic()))
                    .append(", planned percent:").append(value(risk.plannedExitPercent()))
                    .append(", executed percent:").append(value(risk.executedExitPercent())).append("}")
                    .append(", plan execution meaning=").append(complianceMeaning(risk.compliance()))
                    .append(", determination basis=").append(complianceBasisMeaning(risk.compliance())).append('\n');
        }
        out.append("The plan execution meaning describes only a linked trigger and execution, not overall plan quality. "
                + "A matched execution remains matched regardless of PnL. Insufficient linked records do not mean a violation. "
                + "A confirmed terminal lifecycle liquidation is outside risk-plan compliance evaluation. "
                + "Forced exits at chart or session completion are lifecycle events, never configured stop/target triggers. "
                + "A null executionReason means no confirmed execution reason, not proof that no trigger occurred. "
                + "plannedExitQty includes integer floor/minimum-one-share rules; executedExitPercent is the actual position fraction.\n");
        out.append("[User-authored plans, notes, and action reasons; keep their causal scope separate]\n");
        for (ChartQualitativeEvidenceContext chart : context.charts()) {
            if (chart.snapshots().isEmpty() && chart.notes().isEmpty() && chart.tradeActions().isEmpty()) continue;
            out.append("- chart reference=").append(chart.chartId()).append(", active=").append(chart.active())
                    .append(", refreshed=").append(chart.refreshed()).append('\n');
            for (SnapshotAiEvidence snapshot : chart.snapshots()) {
                out.append("  * PRE-TRADE PLAN (generic unless exact action linkage is shown), version ").append(snapshot.version())
                        .append(" authoredAt=").append(value(snapshot.authoredAt()))
                        .append(", timeline=").append(anchor(snapshot.timeline()))
                        .append(", text={thesis:").append(value(snapshot.thesis()))
                        .append(",entryReason:").append(value(snapshot.entryReason()))
                        .append(",exitPlan:").append(value(snapshot.exitPlan()))
                        .append(",riskNote:").append(value(snapshot.riskNote()))
                        .append(",freeNote:").append(value(snapshot.freeNote())).append("}\n");
            }
            for (NoteAiEvidence note : chart.notes()) {
                out.append("  * USER NOTE authoredAt=").append(value(note.authoredAt()))
                        .append(", timeline=").append(anchor(note.timeline()))
                        .append(", summary=").append(value(note.summary()));
                if (note.detail() != null && !note.detail().equals(note.summary())) {
                    out.append(", detail=").append(note.detail());
                }
                out.append('\n');
            }
            for (TradeActionAiEvidence action : chart.tradeActions()) {
                out.append("  * EXECUTED ACTION side=").append(action.side())
                        .append(", candleTime=").append(value(action.candleTime()))
                        .append(", qty=").append(value(action.qty())).append(", price=").append(value(action.price()))
                        .append(", authoredAt=").append(value(action.createdAt()))
                        .append(", timeline=").append(anchor(action.timeline())).append(", reasons=")
                        .append(reasons(action)).append('\n');
                if (action.scenarioPlan() != null) out.append("    EXACT LINKED PRE-TRADE PLAN text={entry condition:")
                        .append(value(action.scenarioPlan().entryReason())).append(",thesis:")
                        .append(value(action.scenarioPlan().thesis())).append(",exit condition:")
                        .append(value(action.scenarioPlan().exitPlan())).append(",risk rationale:")
                        .append(value(action.scenarioPlan().riskNote())).append("}. Link proves plan selection, not condition confirmation.\n");
                else out.append("    EXACT PLAN LINK unavailable; do not infer the action reason from a generic snapshot.\n");
            }
        }
        return out.isEmpty() ? "user-authored snapshot/note/trade reason 없음" : out.toString();
    }

    private String anchor(EvidenceTimelineAnchor anchor) {
        if (anchor == null || "UNRESOLVED".equals(anchor.resolution())) return "연결 시점 확인 불가";
        return "{사용자 기록에 연결 시점 포함,progress=" + value(anchor.progressIndex())
                + ",candleTime=" + value(anchor.candleTime())
                + ",episode=" + value(anchor.episodeReference())
                + ",risk rule linked=" + (anchor.riskRuleHistoryId() != null) + "}";
    }

    private String value(Object value) {
        return value == null ? "undefined" : value.toString().replace('\n', ' ').trim();
    }

    private String reasons(TradeActionAiEvidence action) {
        if (action.reasons().isEmpty()) return "unavailable";
        return action.reasons().stream()
                .map(reason -> "{reason:" + value(reason.entryReason())
                        + ", risk rationale:" + value(reason.riskNote()) + "}")
                .toList().toString();
    }

    private String complianceMeaning(String compliance) {
        if (compliance == null) return "연결된 기록만으로 판단할 수 없음";
        return switch (compliance) {
            case "FOLLOWED" -> "사전에 정한 실행 기준과 실제 청산이 일치함";
            case "NOT_FOLLOWED" -> "사전에 정한 실행 기준과 실제 청산이 일치하지 않음";
            case "NOT_APPLICABLE" -> "차트 또는 세션 종료에 따른 청산으로 리스크 계획 평가 대상이 아님";
            default -> "연결된 기록만으로 계획 이행 여부를 판단할 수 없음";
        };
    }

    private String executionReasonMeaning(String reason) {
        if (reason == null) return "확인된 청산 사유 없음";
        return switch (reason) {
            case "STOP_LOSS" -> "손절 기준 도달";
            case "TAKE_PROFIT" -> "익절 기준 도달";
            case "END_OF_CHART" -> "차트 종료에 따른 청산";
            case "END_OF_SESSION" -> "세션 종료에 따른 청산";
            default -> "기록된 청산 사유 있음";
        };
    }

    private String complianceBasisMeaning(String compliance) {
        if ("FOLLOWED".equals(compliance) || "NOT_FOLLOWED".equals(compliance)) {
            return "연결된 청산 사유, 거래 당시 계획, 실제 실행 수량을 비교했으며 손익은 제외함";
        }
        if ("NOT_APPLICABLE".equals(compliance)) {
            return "차트 또는 세션 종료에 따른 강제 청산이 확인됨";
        }
        return "서로 정확히 연결된 계획과 실행 기록이 충분하지 않으며 위반을 뜻하지 않음";
    }
}
