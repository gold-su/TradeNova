package com.tradenova.report.service;

import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.ChartLifecycleAiEvidence;
import com.tradenova.report.dto.ScenarioPlanAiEvidence;
import com.tradenova.report.dto.TradeActionAiEvidence;
import com.tradenova.training.analytics.DecisionTechnicalContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a point-in-time qualitative input before the model interprets it.
 * It deliberately separates authored claims, observed facts, linkage and behavior.
 */
@Component
public class ChartQualitativeReviewV2Formatter {

    public String format(AiAnalysisRequest request) {
        StringBuilder out = new StringBuilder();
        out.append("[QUALITATIVE REVIEW V2 INPUT]\n")
                .append("Reason in this order: USER CLAIM -> DECISION-TIME FACT -> GAP -> BEHAVIOR -> IMPLICATION -> NEXT DRILL.\n")
                .append("Do not copy these internal labels or classifications into user-facing text.\n\n");

        out.append("LATEST SNAPSHOT CONTEXT (quote without strengthening; this is not precommitment after an action unless exactly linked):\n")
                .append("- thesis: ").append(value(request.thesis())).append('\n')
                .append("- entry statement: ").append(value(request.entryReason())).append('\n')
                .append("  claim quality: ").append(QualitativeEvidenceClassifier.meaning(request.entryReason())).append('\n')
                .append("- narrative exit condition: ").append(value(request.exitPlan())).append('\n')
                .append("- narrative risk rationale: ").append(value(request.riskNote())).append('\n');
        appendActionClaim(out, "entry action", request.entryActionEvidence());
        if (request.latestActionEvidence() != request.entryActionEvidence()) {
            appendActionClaim(out, "latest action", request.latestActionEvidence());
        }

        DecisionTechnicalContext boundedContext = request.entryDecisionTechnicalContext() != null
                ? request.entryDecisionTechnicalContext()
                : request.currentVisibleTechnicalContext();
        out.append("\nDECISION-TIME FACTS (bounded at entry when BUY exists; otherwise current visible decision boundary):\n")
                .append(technicalFacts(boundedContext));

        out.append("\nPLAN-ACTION CONNECTION (causality boundary):\n");
        appendLink(out, "entry action", request.entryActionEvidence());
        appendLink(out, "latest action", request.latestActionEvidence());

        RiskPrecommitmentV21Analyzer.Precommitment precommitment = precommitment(request);
        out.append("\nPRECOMMITMENT:\n")
                .append("- exact action-linked narrative plan: ")
                .append(exactPreActionPlan(request) == null ? "not confirmed" : "confirmed").append('\n')
                .append("- deterministic precommitment assessment: ")
                .append(RiskPrecommitmentV21Analyzer.describe(precommitment)).append('\n')
                .append("- current structured stop setting: ")
                .append(request.stopLossPrice() == null ? "not configured" : "configured").append('\n')
                .append("- current structured target setting: ")
                .append(request.takeProfitPrice() == null ? "not configured" : "configured").append('\n')
                .append("- historical trade-time risk record: ")
                .append(hasHistoricalRisk(request.lifecycleEvidence()) ? "available" : "not confirmed").append('\n')
                .append("A manual conditional rule can be valid precommitment. Automatic execution is a separate execution mechanism, not a prerequisite for plan quality.\n")
                .append("Current settings describe the present state and must not overwrite trade-time history.\n");

        RiskPrecommitmentV21Analyzer.Comparison comparison = planActionComparison(request);
        out.append("\nPLAN -> ACTUAL ACTION COMPARISON:\n")
                .append("- ").append(RiskPrecommitmentV21Analyzer.describe(comparison)).append('\n')
                .append("Transaction completion alone never establishes plan adherence. Compare trigger, action type and size independently.\n");

        out.append("\nBEHAVIOR (canonical execution facts; execution alone is not a strength):\n")
                .append(behavior(request.lifecycleEvidence(), request.latestActionEvidence()));

        List<String> gaps = gaps(request);
        out.append("\nGAP CANDIDATES (interpret only when the supplied facts support them):\n");
        if (gaps.isEmpty()) out.append("- no deterministic gap candidate; do not force caution\n");
        else gaps.forEach(gap -> out.append("- ").append(gap).append('\n'));

        out.append("\nNEXT DRILL CANDIDATE (one concrete practice, never a trade recommendation):\n- ")
                .append(nextDrill(request, gaps)).append('\n');
        return out.toString();
    }

    private void appendActionClaim(StringBuilder out, String label, TradeActionAiEvidence action) {
        if (action == null) {
            out.append("- ").append(label).append(": no canonical action-time reason\n");
            return;
        }
        String reason = primaryReason(action);
        out.append("- ").append(label).append(" side: ").append(value(action.side())).append('\n')
                .append("- ").append(label).append(" user-selected plan relation: ")
                .append(value(action.planRelation())).append('\n')
                .append("- ").append(label).append(" plan change reason: ")
                .append(value(action.changeReason())).append('\n')
                .append("- ").append(label).append(" user reason: ").append(value(reason)).append('\n')
                .append("  action-time explanation specificity: ").append(QualitativeEvidenceClassifier.meaning(reason)).append('\n')
                .append("  boundary: specificity at action time does not make this a pre-action commitment\n");
    }

    private void appendLink(StringBuilder out, String label, TradeActionAiEvidence action) {
        if (action == null || action.scenarioPlan() == null) {
            out.append("- ").append(label)
                    .append(": no exact stored plan relation; chronology alone does not establish causality\n");
            return;
        }
        ScenarioPlanAiEvidence plan = action.scenarioPlan();
        out.append("- ").append(label)
                .append(": exact stored relation confirms this plan was selected, but does not confirm every condition occurred\n")
                .append("  linked entry condition: ").append(value(plan.entryReason())).append('\n')
                .append("  linked exit condition: ").append(value(plan.exitPlan())).append('\n')
                .append("  linked risk rationale: ").append(value(plan.riskNote())).append('\n');
    }

    private String technicalFacts(DecisionTechnicalContext context) {
        if (context == null) return "- no bounded entry technical context\n";
        StringBuilder out = new StringBuilder();
        out.append("- as-of candle time: ").append(value(context.asOfCandleTime())).append('\n');
        if (context.currentClose() != null && context.sma20() != null) {
            out.append("- close relative to SMA20: ").append(relation(context.currentClose(), context.sma20())).append('\n');
        }
        if (context.currentClose() != null && context.sma60() != null) {
            out.append("- close relative to SMA60: ").append(relation(context.currentClose(), context.sma60())).append('\n');
        }
        if (context.rsi14() != null) out.append("- RSI14: ").append(context.rsi14()).append('\n');
        if (context.volumeRatio20() != null) {
            out.append("- volume versus 20-bar average: ")
                    .append(context.volumeRatio20() >= 1.0 ? "at or above average" : "below average")
                    .append(" (calculated ratio ").append(context.volumeRatio20()).append(")\n");
        }
        if (context.return5BarsPct() != null) out.append("- 5-bar return: ").append(context.return5BarsPct()).append("%\n");
        if (context.recentSwingLows() != null && !context.recentSwingLows().isEmpty()) {
            out.append("- recent swing lows are available for structure comparison\n");
        }
        out.append("These are system observations, not claims authored by the user. ")
                .append("Select only 1-3 facts relevant to the user's exact claim. Do not imply the user observed them.\n");
        return out.toString();
    }

    private String relation(double left, double right) {
        if (Double.compare(left, right) > 0) return "above";
        if (Double.compare(left, right) < 0) return "below";
        return "equal";
    }

    private String behavior(ChartLifecycleAiEvidence lifecycle, TradeActionAiEvidence latest) {
        StringBuilder out = new StringBuilder();
        if (lifecycle != null) {
            out.append("- historical BUY execution: ")
                    .append(lifecycle.historicalBuyExecuted() ? "confirmed" : "not confirmed").append('\n');
            out.append("- current position: ")
                    .append(lifecycle.finalPositionClosed() ? "closed terminal state" : "not confirmed closed").append('\n');
            if (lifecycle.terminalLiquidationReason() != null) {
                out.append("- exit classification: lifecycle-forced liquidation; do not assess as discretionary SELL\n");
            }
        }
        if (latest != null) out.append("- latest canonical action side: ").append(value(latest.side())).append('\n');
        return out.toString();
    }

    private List<String> gaps(AiAnalysisRequest request) {
        List<String> gaps = new ArrayList<>();
        String entryReason = primaryReason(request.entryActionEvidence());
        String exitReason = isSell(request.latestActionEvidence()) ? primaryReason(request.latestActionEvidence()) : null;
        if (!present(entryReason)) gaps.add("진입 행동 당시 사용자 이유가 없어 사전 관찰과 행동의 직접 연결을 확인할 수 없음");
        else if (QualitativeEvidenceClassifier.classify(entryReason) == QualitativeEvidenceClassifier.Specificity.VAGUE) {
            gaps.add("진입 이유는 있으나 확인 가능한 가격대·조건으로 바뀌지 않음");
        }
        RiskPrecommitmentV21Analyzer.Precommitment precommitment = precommitment(request);
        if (precommitment.type() == RiskPrecommitmentV21Analyzer.PlanType.NO_PLAN) {
            gaps.add("판단이 틀렸다고 볼 무효화 조건이 사전에 고정되지 않음");
        } else if (precommitment.type() == RiskPrecommitmentV21Analyzer.PlanType.VAGUE) {
            gaps.add("청산 전에 적용할 목표 또는 조건이 고정되지 않음");
        }
        if (request.entryActionEvidence() != null && request.entryActionEvidence().scenarioPlan() == null) {
            gaps.add("사전 계획과 진입 행동 사이의 exact stored relation이 없어 인과를 확정할 수 없음");
        }
        if (QualitativeEvidenceClassifier.isReactiveExit(exitReason)
                && !terminal(request.lifecycleEvidence())
                && (request.latestActionEvidence() == null || request.latestActionEvidence().scenarioPlan() == null)) {
            gaps.add("청산 이유가 사전 기준보다 이미 발생한 가격 변화에 대한 재량적 반응으로 기록됨");
        }
        RiskPrecommitmentV21Analyzer.Comparison comparison = planActionComparison(request);
        if (comparison.relation() == RiskPrecommitmentV21Analyzer.Relation.PARTIAL_DEVIATION
                || comparison.relation() == RiskPrecommitmentV21Analyzer.Relation.DEVIATION) {
            gaps.add("사전 청산 계획과 실제 행동의 조건·행동 유형·비중 중 하나 이상에 차이가 확인됨: "
                    + RiskPrecommitmentV21Analyzer.describe(comparison));
        }
        return gaps;
    }

    private String nextDrill(AiAnalysisRequest request, List<String> gaps) {
        RiskPrecommitmentV21Analyzer.Comparison comparison = planActionComparison(request);
        if (comparison.relation() == RiskPrecommitmentV21Analyzer.Relation.PARTIAL_DEVIATION
                || comparison.relation() == RiskPrecommitmentV21Analyzer.Relation.DEVIATION) {
            return "다음 훈련에서는 사전에 정한 청산 조건과 비중을 실제 행동에 맞춰 보세요. 계획을 바꿀 때는 실행 전에 변경 이유를 기록해 보세요";
        }
        RiskPrecommitmentV21Analyzer.Precommitment precommitment = precommitment(request);
        if (precommitment.type() == RiskPrecommitmentV21Analyzer.PlanType.NO_PLAN) {
            return "다음 훈련의 BUY 전에 판단이 틀렸다고 볼 가격 또는 구조를 한 문장으로 기록해 보세요";
        }
        if (precommitment.type() == RiskPrecommitmentV21Analyzer.PlanType.VAGUE) {
            return "다음 훈련의 진입 전에 청산을 검토할 가격대 또는 확인 조건 하나를 기록해 보세요";
        }
        if (request.entryActionEvidence() != null && request.entryActionEvidence().scenarioPlan() == null) {
            return "다음 행동 이유에 사전 계획의 어떤 조건을 확인했는지 한 가지 직접 연결해 기록해 보세요";
        }
        if (!gaps.isEmpty()) return "다음 행동 전에 관찰을 검증 가능한 조건과 반대 근거 하나로 바꿔 기록해 보세요";
        return "다음 훈련에서도 행동 전에 조건·무효화·청산 기준을 기록하고 실제 이유와 연결해 보세요";
    }

    private RiskPrecommitmentV21Analyzer.Precommitment precommitment(AiAnalysisRequest request) {
        boolean historicalAction = request.lifecycleEvidence() != null
                && request.lifecycleEvidence().historicalBuyExecuted();
        if (!historicalAction) {
            ScenarioPlanAiEvidence exactWaitPlan = exactWaitPlan(request);
            RiskPrecommitmentV21Analyzer.Precommitment narrative = exactWaitPlan == null
                    ? RiskPrecommitmentV21Analyzer.classify(request.exitPlan(), request.riskNote(), null, null, false)
                    : RiskPrecommitmentV21Analyzer.classify(exactWaitPlan);
            RiskPrecommitmentV21Analyzer.Precommitment currentStructured = RiskPrecommitmentV21Analyzer.classify(
                    null, null, request.stopLossPrice(),
                    request.takeProfitPrice(), request.autoExitEnabled());
            return RiskPrecommitmentV21Analyzer.stronger(narrative, currentStructured);
        }
        RiskPrecommitmentV21Analyzer.Precommitment narrative =
                RiskPrecommitmentV21Analyzer.classify(exactEntryPlan(request));
        if (!hasHistoricalRisk(request.lifecycleEvidence())) return narrative;
        RiskPrecommitmentV21Analyzer.Precommitment structured = RiskPrecommitmentV21Analyzer.classify(
                null, null, BigDecimal.ONE, null,
                request.lifecycleEvidence().historicalAutoExitEnabled());
        return RiskPrecommitmentV21Analyzer.stronger(narrative, structured);
    }

    private ScenarioPlanAiEvidence exactEntryPlan(AiAnalysisRequest request) {
        return request.entryActionEvidence() == null ? null : request.entryActionEvidence().scenarioPlan();
    }

    private ScenarioPlanAiEvidence exactPreActionPlan(AiAnalysisRequest request) {
        ScenarioPlanAiEvidence entry = exactEntryPlan(request);
        return entry != null ? entry : exactWaitPlan(request);
    }

    private ScenarioPlanAiEvidence exactWaitPlan(AiAnalysisRequest request) {
        TradeActionAiEvidence latest = request.latestActionEvidence();
        return latest != null && "WAIT".equalsIgnoreCase(latest.side()) ? latest.scenarioPlan() : null;
    }

    private RiskPrecommitmentV21Analyzer.Comparison planActionComparison(AiAnalysisRequest request) {
        TradeActionAiEvidence action = isSell(request.latestActionEvidence()) ? request.latestActionEvidence() : null;
        ScenarioPlanAiEvidence plan = action == null ? null : action.scenarioPlan();
        BigDecimal positionBefore = null;
        if (action != null && action.qty() != null && request.positionQty() != null) {
            positionBefore = action.qty().add(request.positionQty());
        }
        return RiskPrecommitmentV21Analyzer.compare(plan, action, positionBefore);
    }

    private String primaryReason(TradeActionAiEvidence action) {
        if (action == null || action.reasons().isEmpty()) return null;
        return action.reasons().stream()
                .map(reason -> reason.entryReason())
                .filter(this::present)
                .findFirst()
                .orElse(null);
    }

    private boolean isSell(TradeActionAiEvidence action) {
        return action != null && "SELL".equalsIgnoreCase(action.side());
    }

    private boolean terminal(ChartLifecycleAiEvidence lifecycle) {
        return lifecycle != null && lifecycle.terminalLiquidationReason() != null;
    }

    private boolean hasHistoricalRisk(ChartLifecycleAiEvidence lifecycle) {
        return lifecycle != null && lifecycle.entryRiskRuleHistoryId() != null;
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private String value(Object value) {
        return value == null || value.toString().isBlank() ? "not recorded" : value.toString().replace('\n', ' ').strip();
    }
}
