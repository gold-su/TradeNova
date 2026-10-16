package com.tradenova.report.service;

import com.tradenova.report.dto.ChartQualitativeEvidenceContext;
import com.tradenova.report.dto.NoteAiEvidence;
import com.tradenova.report.dto.RiskComplianceAiEvidence;
import com.tradenova.report.dto.SessionQualitativeEvidenceContext;
import com.tradenova.report.dto.SnapshotAiEvidence;
import com.tradenova.report.dto.TradeActionAiEvidence;
import org.springframework.stereotype.Component;

import java.util.List;

/** Builds session-level qualitative units without turning event logs into user-facing prose. */
@Component
public class SessionQualitativeEvidenceFormatter {

    public String format(SessionQualitativeEvidenceContext context) {
        if (context == null) return "qualitative context unavailable";

        StringBuilder out = new StringBuilder();
        out.append("[SESSION QUALITATIVE REVIEW V2 INPUT]\n")
                .append("Synthesize behavior in this order: USER CLAIM -> FACT -> GAP -> BEHAVIOR -> IMPLICATION -> NEXT DRILL.\n")
                .append("These labels and classifications are internal reasoning aids. Never copy them into user-facing text.\n")
                .append("No-trade charts without an explicit user-authored decision are excluded. Chart counts are neutral.\n")
                .append("A plan before an action does not prove causality. Only an exact stored relation confirms plan selection; it still does not prove every condition occurred.\n\n");

        SynthesisSignals signals = new SynthesisSignals();
        int unit = 0;
        for (ChartQualitativeEvidenceContext chart : context.charts()) {
            if (chart.snapshots().isEmpty() && chart.notes().isEmpty() && chart.tradeActions().isEmpty()) continue;
            unit++;
            out.append("ANALYSIS UNIT ").append(unit).append("\n");
            appendPlans(out, chart.snapshots(), signals);
            appendNotes(out, chart.notes());
            appendActions(out, chart.tradeActions(), signals);
            out.append('\n');
        }

        appendRiskExecution(out, context.riskCompliance());
        out.append("\nSESSION SYNTHESIS SIGNALS (counts are not user-facing):\n")
                .append("- analyzed units: ").append(unit).append('\n')
                .append("- vague or unconditioned action reasons: ").append(signals.weakReasons).append('\n')
                .append("- actions without exact plan relation: ").append(signals.unlinkedActions).append('\n')
                .append("- reactive discretionary exit candidates: ").append(signals.reactiveExits).append('\n')
                .append("- plans without an invalidation rationale: ").append(signals.plansWithoutInvalidation).append('\n')
                .append("Use 'repeated pattern' only when the same characteristic appears in at least two independent actions or units. ")
                .append("For one unit, describe one prominent behavior characteristic instead.\n")
                .append("Do not recap every action. Convert the strongest cross-action signal into one implication and one concrete next drill.\n");
        return out.toString();
    }

    private void appendPlans(StringBuilder out, List<SnapshotAiEvidence> snapshots, SynthesisSignals signals) {
        if (snapshots.isEmpty()) {
            out.append("PRE-TRADE USER CLAIM: not recorded\n");
            return;
        }
        for (SnapshotAiEvidence snapshot : snapshots) {
            out.append("PRE-TRADE USER CLAIM (generic unless an action below has an exact relation):\n")
                    .append("- thesis: ").append(value(snapshot.thesis())).append('\n')
                    .append("- entry statement: ").append(value(snapshot.entryReason())).append('\n')
                    .append("  claim quality: ").append(QualitativeEvidenceClassifier.meaning(snapshot.entryReason())).append('\n')
                    .append("- exit condition: ").append(value(snapshot.exitPlan())).append('\n')
                    .append("- invalidation/risk rationale: ").append(value(snapshot.riskNote())).append('\n');
            if (!present(snapshot.riskNote())) signals.plansWithoutInvalidation++;
        }
    }

    private void appendNotes(StringBuilder out, List<NoteAiEvidence> notes) {
        for (NoteAiEvidence note : notes) {
            out.append("USER NOTE (do not assign it to an earlier action unless its timeline is resolved): ")
                    .append(value(note.summary())).append('\n');
        }
    }

    private void appendActions(StringBuilder out, List<TradeActionAiEvidence> actions, SynthesisSignals signals) {
        if (actions.isEmpty()) {
            out.append("BEHAVIOR: no canonical trade action in this unit\n");
            return;
        }
        int actionIndex = 0;
        for (TradeActionAiEvidence action : actions) {
            actionIndex++;
            String reason = primaryReason(action);
            QualitativeEvidenceClassifier.Specificity specificity = QualitativeEvidenceClassifier.classify(reason);
            out.append("ACTION ").append(actionIndex).append("\n")
                    .append("- behavior: ").append(value(action.side())).append('\n')
                    .append("- user-authored action reason: ").append(value(reason)).append('\n')
                    .append("- reason quality: ").append(QualitativeEvidenceClassifier.meaning(reason)).append('\n');
            if (specificity == QualitativeEvidenceClassifier.Specificity.VAGUE
                    || specificity == QualitativeEvidenceClassifier.Specificity.OBSERVATION_ONLY) {
                signals.weakReasons++;
            }

            if (action.scenarioPlan() == null) {
                signals.unlinkedActions++;
                out.append("- plan connection: no exact stored relation; do not say the plan caused or matched the action\n");
            } else {
                out.append("- plan connection: exact stored relation confirms plan selection, not condition occurrence\n")
                        .append("- linked entry condition: ").append(value(action.scenarioPlan().entryReason())).append('\n')
                        .append("- linked exit condition: ").append(value(action.scenarioPlan().exitPlan())).append('\n')
                        .append("- linked invalidation rationale: ").append(value(action.scenarioPlan().riskNote())).append('\n');
            }

            if ("WAIT".equalsIgnoreCase(action.side()) && action.scenarioPlan() != null) {
                out.append("- behavior interpretation boundary: an explicitly linked WAIT may show execution discipline; no trade is not a weakness\n");
            }
            if ("SELL".equalsIgnoreCase(action.side())
                    && action.scenarioPlan() == null
                    && QualitativeEvidenceClassifier.isReactiveExit(reason)) {
                signals.reactiveExits++;
                out.append("- gap candidate: exit reason describes price movement already observed and has no exact preplanned connection; treat as a reactive-exit candidate, not proven plan adherence\n");
            }
        }
    }

    private void appendRiskExecution(StringBuilder out, List<RiskComplianceAiEvidence> risks) {
        out.append("RISK EXECUTION FACTS (execution scope only; separate from plan quality and user intent):\n");
        if (risks.isEmpty()) {
            out.append("- no exactly linked risk-execution comparison\n");
            return;
        }
        for (RiskComplianceAiEvidence risk : risks) {
            out.append("- configured at trade: stop ").append(configured(risk.stopLossConfigured()))
                    .append(", target ").append(configured(risk.takeProfitConfigured()))
                    .append(", automatic exit ").append(configured(risk.autoExitEnabledAtTrade())).append('\n')
                    .append("- observed exit: ").append(executionReasonMeaning(risk.executionReason()))
                    .append(", automatic execution ").append(configured(risk.actualExitAutomatic())).append('\n')
                    .append("- execution relationship: ").append(complianceMeaning(risk.compliance())).append('\n');
        }
        out.append("A matched risk execution is not proof of plan quality. A lifecycle-forced liquidation is outside risk-plan compliance and is not a discretionary SELL.\n");
    }

    private String primaryReason(TradeActionAiEvidence action) {
        return action.reasons().stream()
                .map(reason -> reason.entryReason())
                .filter(this::present)
                .findFirst()
                .orElse(null);
    }

    private String configured(Boolean value) {
        if (value == null) return "not confirmed";
        return value ? "configured" : "not configured";
    }

    private String complianceMeaning(String compliance) {
        if (compliance == null) return "not enough exactly linked records; this is not a violation";
        return switch (compliance) {
            case "FOLLOWED" -> "the configured trigger and executed exit matched";
            case "NOT_FOLLOWED" -> "the configured trigger and executed exit did not match";
            case "NOT_APPLICABLE" -> "lifecycle-forced liquidation; exclude from risk-plan compliance";
            default -> "not enough exactly linked records; this is not a violation";
        };
    }

    private String executionReasonMeaning(String reason) {
        if (reason == null) return "no exact exit reason confirmed";
        return switch (reason) {
            case "STOP_LOSS" -> "configured stop trigger";
            case "TAKE_PROFIT" -> "configured target trigger";
            case "END_OF_CHART" -> "chart-completion lifecycle liquidation";
            case "END_OF_SESSION" -> "session-completion lifecycle liquidation";
            default -> "recorded exit reason";
        };
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private String value(Object value) {
        return value == null || value.toString().isBlank() ? "not recorded" : value.toString().replace('\n', ' ').strip();
    }

    private static final class SynthesisSignals {
        private int weakReasons;
        private int unlinkedActions;
        private int reactiveExits;
        private int plansWithoutInvalidation;
    }
}
