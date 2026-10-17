package com.tradenova.report.service;

import com.tradenova.report.dto.ScenarioPlanAiEvidence;
import com.tradenova.report.dto.TradeActionAiEvidence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic risk-precommitment and plan/action comparison hints for AI Review V2.1. */
final class RiskPrecommitmentV21Analyzer {

    enum PlanType {
        NO_PLAN,
        VAGUE,
        CONDITIONAL,
        PRICE_BASED,
        AUTOMATED
    }

    enum Relation {
        MATCH,
        PARTIAL_DEVIATION,
        DEVIATION,
        ADAPTATION_CANDIDATE,
        UNVERIFIABLE
    }

    enum DimensionRelation {
        MATCH,
        MISMATCH,
        UNVERIFIABLE
    }

    record Precommitment(PlanType type, boolean narrativeCondition, boolean numericSetting,
                         boolean automatedExecution) {
        boolean exists() {
            return type != PlanType.NO_PLAN;
        }

        boolean verifiable() {
            return type == PlanType.CONDITIONAL || type == PlanType.PRICE_BASED || type == PlanType.AUTOMATED;
        }
    }

    record Comparison(Relation relation, DimensionRelation trigger, DimensionRelation action,
                      DimensionRelation size, BigDecimal plannedPercent, BigDecimal actualPercent,
                      boolean planChangeRecorded, String plannedTrigger, String actualTrigger) {
    }

    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final Pattern PLAN_CHANGE = Pattern.compile(".*(계획.{0,8}(변경|수정)|(변경|수정).{0,8}계획).*");
    private static final Pattern CHANGE_RATIONALE = Pattern.compile(
            ".*(새로운?\\s*정보|new information|이유|때문|따라|확인되어|확인돼|관찰되어|관찰돼).*"
    );

    private RiskPrecommitmentV21Analyzer() {
    }

    static Precommitment classify(String exitPlan, String riskNote, BigDecimal stopLossPrice,
                                  BigDecimal takeProfitPrice, Boolean autoExitEnabled) {
        String narrative = join(exitPlan, riskNote);
        boolean numeric = stopLossPrice != null || takeProfitPrice != null;
        boolean automated = numeric && Boolean.TRUE.equals(autoExitEnabled);
        boolean conditioned = Arrays.stream(new String[]{exitPlan, riskNote})
                .anyMatch(value -> QualitativeEvidenceClassifier.classify(value)
                        == QualitativeEvidenceClassifier.Specificity.CONDITIONED);
        boolean narrativePresent = present(narrative);

        if (automated) return new Precommitment(PlanType.AUTOMATED, conditioned, true, true);
        if (numeric) return new Precommitment(PlanType.PRICE_BASED, conditioned, true, false);
        if (conditioned) return new Precommitment(PlanType.CONDITIONAL, true, false, false);
        if (narrativePresent) return new Precommitment(PlanType.VAGUE, false, false, false);
        return new Precommitment(PlanType.NO_PLAN, false, false, false);
    }

    static Precommitment classify(ScenarioPlanAiEvidence plan) {
        if (plan == null) return classify(null, null, null, null, false);
        return classify(plan.exitPlan(), plan.riskNote(), null, null, false);
    }

    static Comparison compare(ScenarioPlanAiEvidence plan, TradeActionAiEvidence action,
                              BigDecimal positionBeforeAction) {
        if (plan == null || action == null || !"SELL".equalsIgnoreCase(action.side())) {
            return unverifiable();
        }

        String actualReason = primaryReason(action);
        List<PlanClause> clauses = clauses(plan.exitPlan(), plan.riskNote());
        if (clauses.isEmpty()) return unverifiable();

        BigDecimal actualPercent = actualPercent(action.qty(), positionBeforeAction);
        String actualTrigger = trigger(actualReason);
        PlanClause selected = selectClause(clauses, actualTrigger, actualPercent);
        DimensionRelation triggerRelation = compareTrigger(selected.trigger(), actualTrigger);

        String plannedAction = actionType(selected.text(), selected.percent());
        String actualAction = actualActionType(actualReason, actualPercent);
        DimensionRelation actionRelation = compareDimension(plannedAction, actualAction);
        DimensionRelation sizeRelation = compareSize(selected.percent(), actualPercent);
        boolean adaptation = isPlanChange(actualReason);

        Relation relation;
        if (adaptation) {
            relation = Relation.ADAPTATION_CANDIDATE;
        } else {
            long matches = List.of(triggerRelation, actionRelation, sizeRelation).stream()
                    .filter(value -> value == DimensionRelation.MATCH).count();
            long mismatches = List.of(triggerRelation, actionRelation, sizeRelation).stream()
                    .filter(value -> value == DimensionRelation.MISMATCH).count();
            long unresolved = List.of(triggerRelation, actionRelation, sizeRelation).stream()
                    .filter(value -> value == DimensionRelation.UNVERIFIABLE).count();
            if (mismatches == 0 && unresolved == 0 && matches > 0) relation = Relation.MATCH;
            else if (mismatches > 0 && matches > 0) relation = Relation.PARTIAL_DEVIATION;
            else if (mismatches > 0) relation = Relation.DEVIATION;
            else relation = Relation.UNVERIFIABLE;
        }

        return new Comparison(relation, triggerRelation, actionRelation, sizeRelation,
                selected.percent(), actualPercent, adaptation, selected.trigger(), actualTrigger);
    }

    static BigDecimal positionBefore(List<TradeActionAiEvidence> actions, int targetIndex) {
        BigDecimal position = BigDecimal.ZERO;
        for (int index = 0; index < targetIndex; index++) {
            TradeActionAiEvidence action = actions.get(index);
            if (action.qty() == null) continue;
            if ("BUY".equalsIgnoreCase(action.side())) position = position.add(action.qty());
            if ("SELL".equalsIgnoreCase(action.side())) position = position.subtract(action.qty()).max(BigDecimal.ZERO);
        }
        return position.signum() > 0 ? position : null;
    }

    static String describe(Precommitment precommitment) {
        return switch (precommitment.type()) {
            case NO_PLAN -> "no pre-action invalidation or exit condition was recorded";
            case VAGUE -> "an exit or risk intention was recorded, but it is not a verifiable condition";
            case CONDITIONAL -> "a verifiable structural or conditional exit rule was recorded before action";
            case PRICE_BASED -> "a concrete price-based exit rule was configured for manual execution";
            case AUTOMATED -> "a concrete price-based exit rule and automatic execution were configured";
        };
    }

    static String describe(Comparison comparison) {
        if (comparison.relation() == Relation.UNVERIFIABLE) {
            return "plan/action relationship cannot be confirmed from the exact linked records";
        }
        if (comparison.relation() == Relation.ADAPTATION_CANDIDATE) {
            return "the action-time record states both a plan change and its rationale; treat this as a possible adaptation, but do not call it justified unless pre-action timing is confirmed";
        }
        List<String> parts = new ArrayList<>();
        parts.add("trigger " + relationText(comparison.trigger()));
        parts.add("action type " + relationText(comparison.action()));
        if (comparison.plannedPercent() != null || comparison.actualPercent() != null) {
            parts.add("position size " + relationText(comparison.size())
                    + percentDetail(comparison.plannedPercent(), comparison.actualPercent()));
        }
        return String.join(", ", parts);
    }

    private static String percentDetail(BigDecimal planned, BigDecimal actual) {
        if (planned == null || actual == null) return "";
        return " (planned " + display(planned) + "%, observed " + display(actual) + "%)";
    }

    private static String display(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String relationText(DimensionRelation relation) {
        return switch (relation) {
            case MATCH -> "matches";
            case MISMATCH -> "differs";
            case UNVERIFIABLE -> "cannot be confirmed";
        };
    }

    private static Comparison unverifiable() {
        return new Comparison(Relation.UNVERIFIABLE, DimensionRelation.UNVERIFIABLE,
                DimensionRelation.UNVERIFIABLE, DimensionRelation.UNVERIFIABLE,
                null, null, false, null, null);
    }

    private static List<PlanClause> clauses(String... texts) {
        List<PlanClause> result = new ArrayList<>();
        for (String text : texts) {
            if (!present(text)) continue;
            for (String raw : text.split("[\\n;]+")) {
                String clause = raw.strip();
                if (!clause.isEmpty()) result.add(new PlanClause(clause, trigger(clause), percentage(clause)));
            }
        }
        return result;
    }

    private static PlanClause selectClause(List<PlanClause> clauses, String actualTrigger,
                                           BigDecimal actualPercent) {
        if (actualTrigger != null) {
            for (PlanClause clause : clauses) {
                if (actualTrigger.equals(clause.trigger())) return clause;
            }
        }
        if (actualPercent != null) {
            for (PlanClause clause : clauses) {
                if (clause.percent() != null && clause.percent().compareTo(actualPercent) == 0) return clause;
            }
        }
        return clauses.get(0);
    }

    private static DimensionRelation compareTrigger(String planned, String actual) {
        if (planned == null || actual == null) return DimensionRelation.UNVERIFIABLE;
        return planned.equals(actual) ? DimensionRelation.MATCH : DimensionRelation.MISMATCH;
    }

    private static DimensionRelation compareDimension(String planned, String actual) {
        if (planned == null || actual == null) return DimensionRelation.UNVERIFIABLE;
        return planned.equals(actual) ? DimensionRelation.MATCH : DimensionRelation.MISMATCH;
    }

    private static DimensionRelation compareSize(BigDecimal planned, BigDecimal actual) {
        if (planned == null || actual == null) return DimensionRelation.UNVERIFIABLE;
        return planned.compareTo(actual) == 0 ? DimensionRelation.MATCH : DimensionRelation.MISMATCH;
    }

    private static BigDecimal actualPercent(BigDecimal qty, BigDecimal positionBefore) {
        if (qty == null || positionBefore == null || positionBefore.signum() <= 0) return null;
        return qty.multiply(BigDecimal.valueOf(100)).divide(positionBefore, 2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private static BigDecimal percentage(String text) {
        if (!present(text)) return null;
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("전량") || normalized.contains("풀매도") || normalized.contains("모두 청산")) {
            return BigDecimal.valueOf(100);
        }
        if (normalized.contains("절반") || normalized.matches(".*반\\s*(손절|매도|청산).*")) {
            return BigDecimal.valueOf(50);
        }
        Matcher matcher = PERCENT.matcher(normalized);
        if (!matcher.find()) return null;
        try {
            return new BigDecimal(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String actionType(String text, BigDecimal percent) {
        if (percent != null) return percent.compareTo(BigDecimal.valueOf(100)) >= 0 ? "FULL_EXIT" : "PARTIAL_EXIT";
        if (!present(text)) return null;
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("전량") || normalized.contains("풀매도") || normalized.contains("모두 청산")) return "FULL_EXIT";
        if (normalized.contains("일부") || normalized.contains("부분") || normalized.contains("축소")) return "PARTIAL_EXIT";
        return null;
    }

    private static String actualActionType(String reason, BigDecimal actualPercent) {
        if (actualPercent != null) return actualPercent.compareTo(BigDecimal.valueOf(100)) >= 0 ? "FULL_EXIT" : "PARTIAL_EXIT";
        return actionType(reason, percentage(reason));
    }

    private static String trigger(String text) {
        if (!present(text)) return null;
        String normalized = text.toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.contains("20일선") || normalized.contains("20일이동평균") || normalized.contains("sma20")) return "20-period moving-average condition";
        if (normalized.contains("전저점") || normalized.contains("직전저점") || normalized.contains("swinglow")) return "prior swing-low condition";
        if (normalized.contains("돌파실패") && (normalized.contains("range") || normalized.contains("박스") || normalized.contains("범위"))) return "failed-breakout range-return condition";
        if (normalized.contains("거래량감소") && (normalized.contains("전고점") || normalized.contains("돌파실패"))) return "volume-decrease breakout-failure condition";
        if ((normalized.contains("올라") || normalized.contains("상승")) && containsPercent(normalized)) return "percentage-gain condition";
        if ((normalized.contains("내려") || normalized.contains("하락")) && containsPercent(normalized)) return "percentage-loss condition";
        if (QualitativeEvidenceClassifier.classify(text) == QualitativeEvidenceClassifier.Specificity.CONDITIONED) {
            return "other explicit condition: " + normalized;
        }
        return null;
    }

    private static boolean isPlanChange(String reason) {
        if (!present(reason)) return false;
        String normalized = reason.toLowerCase(Locale.ROOT);
        return PLAN_CHANGE.matcher(normalized).matches() && CHANGE_RATIONALE.matcher(normalized).matches();
    }

    static Precommitment stronger(Precommitment left, Precommitment right) {
        return rank(right.type()) > rank(left.type()) ? right : left;
    }

    private static int rank(PlanType type) {
        return switch (type) {
            case NO_PLAN -> 0;
            case VAGUE -> 1;
            case CONDITIONAL, PRICE_BASED, AUTOMATED -> 2;
        };
    }

    private static boolean containsPercent(String value) {
        return PERCENT.matcher(value).find();
    }

    private static String primaryReason(TradeActionAiEvidence action) {
        if (action == null) return null;
        return action.reasons().stream()
                .map(reason -> join(reason.entryReason(), reason.riskNote()))
                .filter(RiskPrecommitmentV21Analyzer::present)
                .findFirst().orElse(null);
    }

    private static String join(String... values) {
        return Arrays.stream(values).filter(RiskPrecommitmentV21Analyzer::present)
                .reduce((left, right) -> left + "; " + right).orElse(null);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private record PlanClause(String text, String trigger, BigDecimal percent) {
    }
}
