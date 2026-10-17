package com.tradenova.report.service;

import com.tradenova.report.dto.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic, outcome-independent scoring rubric for Chart and Session AI reviews. */
final class AiReviewScoreCalculator {

    private static final Pattern NUMBER = Pattern.compile(".*\\d.*");
    private static final List<String> EVIDENCE_TERMS = List.of(
            "이동평균", "이평", "거래량", "돌파", "이탈", "반등", "고점", "저점", "지지", "저항",
            "종가", "추세", "rsi", "atr", "변동성", "조건", "부근", "위에서", "아래에서", "증가", "감소"
    );
    private static final List<String> VAGUE_TERMS = List.of(
            "오를 것 같", "내릴 것 같", "많이 올라", "많이 내려", "그냥", "떨어지면", "이평 보고",
            "이동평균선 보고", "느낌", "왠지"
    );
    private static final List<String> CONDITION_TERMS = List.of(
            "돌파", "이탈", "반등", "유지", "접근", "둔화", "증가 시", "감소 시", "깨지", "경우", "이면", "하면"
    );
    private static final List<String> ACTION_TERMS = List.of(
            "진입", "매수", "손절", "익절", "매도", "청산", "관망", "일부"
    );

    private AiReviewScoreCalculator() {
    }

    static Score score(AiAnalysisRequest request) {
        int entry = best(entryTexts(request));
        int exitPlan = best(exitPlanTexts(request));
        int risk = best(riskTexts(request));
        int planRisk = best(precommitmentRiskTexts(request));
        boolean sell = request.lifecycleEvidence() != null
                && "SELL".equals(request.lifecycleEvidence().latestTradeSide());
        int exit = sell ? actionSpecificity(request.latestActionEvidence()) : exitPlan;

        int decision = decisionScore(entry, exit);
        RiskPrecommitmentV21Analyzer.Precommitment precommitment = precommitment(request);
        boolean historicalAction = historicalBuy(request);
        int plan = planScore(
                !historicalAction && request.stopLossPrice() != null,
                !historicalAction && request.takeProfitPrice() != null,
                precommitment,
                planRisk,
                exitPlan
        );
        boolean buy = request.lifecycleEvidence() != null && request.lifecycleEvidence().historicalBuyExecuted();
        int actionReason = averageActionSpecificity(request.entryActionEvidence(), request.latestActionEvidence());
        int execution = executionScore(actionReason, false);
        int reviewability = reviewabilityScore(entry, exit, risk);
        return finish(decision, plan, execution, reviewability, entry, exit, risk, buy || sell, actionReason);
    }

    static Score score(SessionAiAnalysisRequest request) {
        List<String> entryTexts = new ArrayList<>();
        List<String> exitPlanTexts = new ArrayList<>();
        List<String> sellReasonTexts = new ArrayList<>();
        List<String> riskTexts = new ArrayList<>();
        List<String> precommitmentExitTexts = new ArrayList<>();
        List<String> precommitmentRiskTexts = new ArrayList<>();
        if (request.qualitativeEvidenceContext() == null && request.snapshots() != null) {
            request.snapshots().forEach(snapshot -> {
                entryTexts.add(snapshot.thesis());
                entryTexts.add(snapshot.entryReason());
                exitPlanTexts.add(snapshot.exitPlan());
                riskTexts.add(snapshot.riskNote());
                precommitmentExitTexts.add(snapshot.exitPlan());
                precommitmentRiskTexts.add(snapshot.riskNote());
            });
        }

        boolean buy = false;
        boolean sell = false;
        List<Integer> actionSpecificities = new ArrayList<>();
        boolean stop = false;
        boolean target = false;
        boolean followed = false;
        RiskPrecommitmentV21Analyzer.Precommitment precommitment = RiskPrecommitmentV21Analyzer.classify(
                null, null, null, null, false);
        if (request.qualitativeEvidenceContext() != null) {
            for (ChartQualitativeEvidenceContext chart : request.qualitativeEvidenceContext().charts()) {
                for (TradeActionAiEvidence action : chart.tradeActions()) {
                    buy |= "BUY".equals(action.side());
                    sell |= "SELL".equals(action.side());
                    actionSpecificities.add(actionSpecificity(action));
                    addScenarioTexts(action.scenarioPlan(), entryTexts, exitPlanTexts, riskTexts);
                    addScenarioTexts(action.scenarioPlan(), new ArrayList<>(),
                            precommitmentExitTexts, precommitmentRiskTexts);
                    precommitment = RiskPrecommitmentV21Analyzer.stronger(precommitment,
                            RiskPrecommitmentV21Analyzer.classify(action.scenarioPlan()));
                    if ("BUY".equals(action.side())) addReasonTexts(action, entryTexts, riskTexts);
                    if ("SELL".equals(action.side())) addReasonTexts(action, sellReasonTexts, riskTexts);
                }
            }
            for (RiskComplianceAiEvidence evidence : request.qualitativeEvidenceContext().riskCompliance()) {
                stop |= Boolean.TRUE.equals(evidence.stopLossConfigured());
                target |= Boolean.TRUE.equals(evidence.takeProfitConfigured());
                precommitment = RiskPrecommitmentV21Analyzer.stronger(precommitment, RiskPrecommitmentV21Analyzer.classify(
                        null, null,
                        Boolean.TRUE.equals(evidence.stopLossConfigured()) ? BigDecimal.ONE : null,
                        Boolean.TRUE.equals(evidence.takeProfitConfigured()) ? BigDecimal.ONE : null,
                        evidence.autoExitEnabledAtTrade()));
                followed |= "FOLLOWED".equals(evidence.compliance());
            }
        }
        if (request.qualitativeEvidenceContext() == null && request.snapshots() != null) {
            for (SessionSnapshotSummary snapshot : request.snapshots()) {
                precommitment = RiskPrecommitmentV21Analyzer.stronger(precommitment, RiskPrecommitmentV21Analyzer.classify(
                        snapshot.exitPlan(), snapshot.riskNote(), null, null, false));
            }
        }

        int entry = best(entryTexts);
        int exitPlan = best(exitPlanTexts);
        int exit = sell ? best(sellReasonTexts) : exitPlan;
        int risk = best(riskTexts);
        int planExit = best(precommitmentExitTexts);
        int planRisk = best(precommitmentRiskTexts);
        int actionReason = average(actionSpecificities);
        return finish(
                decisionScore(entry, exit),
                planScore(stop, target, precommitment, planRisk, planExit),
                executionScore(actionReason, followed),
                reviewabilityScore(entry, exit, risk),
                entry, exit, risk, buy || sell, actionReason
        );
    }

    private static Score finish(int decision, int plan, int execution, int reviewability,
                                int entrySpecificity, int exitSpecificity, int riskSpecificity,
                                boolean hasExecutedAction, int actionReasonSpecificity) {
        int raw = decision + plan + execution + reviewability;
        int cap = 100;
        if (entrySpecificity <= 1 && exitSpecificity <= 1) cap = Math.min(cap, plan >= 17 ? 69 : 59);
        if (entrySpecificity <= 2 && exitSpecificity <= 2) cap = Math.min(cap, 79);
        if (entrySpecificity <= 1 && riskSpecificity <= 1) cap = Math.min(cap, 69);
        if (hasExecutedAction && actionReasonSpecificity == 0) cap = Math.min(cap, 79);
        return new Score(Math.min(raw, cap), decision, plan, execution, reviewability, cap,
                entrySpecificity, exitSpecificity, riskSpecificity, actionReasonSpecificity);
    }

    private static int decisionScore(int entry, int exit) {
        return points(entry, 0, 13, 18, 25) + points(exit, 0, 7, 10, 15);
    }

    private static int planScore(boolean stop, boolean target,
                                 RiskPrecommitmentV21Analyzer.Precommitment precommitment,
                                 int risk, int exit) {
        int structuredBasis = (stop ? 5 : 0) + (target ? 5 : 0);
        int semanticBasis = switch (precommitment.type()) {
            case NO_PLAN -> 0;
            case VAGUE -> 3;
            case CONDITIONAL, PRICE_BASED, AUTOMATED -> 10;
        };
        int score = Math.max(structuredBasis, semanticBasis);
        score += points(risk, 0, 1, 4, 8);
        score += points(exit, 0, 1, 4, 7);
        return Math.min(25, score);
    }

    private static int executionScore(int actionReason, boolean followed) {
        int score = points(actionReason, 0, 4, 8, 12);
        if (followed) score += 8;
        return Math.min(20, score);
    }

    private static int reviewabilityScore(int entry, int exit, int risk) {
        return Math.min(15,
                points(entry, 0, 3, 5, 6)
                        + points(exit, 0, 3, 5, 6)
                        + points(risk, 0, 2, 3, 3));
    }

    private static int points(int level, int none, int vague, int grounded, int specific) {
        return switch (level) {
            case 1 -> vague;
            case 2 -> grounded;
            case 3 -> specific;
            default -> none;
        };
    }

    private static int best(List<String> texts) {
        return texts.stream().mapToInt(AiReviewScoreCalculator::specificity).max().orElse(0);
    }

    private static int specificity(String value) {
        if (value == null || value.isBlank()) return 0;
        String text = value.strip().toLowerCase(Locale.ROOT);
        boolean vague = VAGUE_TERMS.stream().anyMatch(text::contains);
        long evidenceTerms = EVIDENCE_TERMS.stream().filter(text::contains).count();
        long conditionTerms = CONDITION_TERMS.stream().filter(text::contains).count();
        boolean actionLinked = ACTION_TERMS.stream().anyMatch(text::contains);
        if (vague) return 1;
        if (NUMBER.matcher(text).matches() && evidenceTerms >= 2 && actionLinked) return 3;
        if (NUMBER.matcher(text).matches() && conditionTerms > 0 && actionLinked) return 3;
        if (evidenceTerms >= 2 && conditionTerms >= 2 && actionLinked) return 3;
        if ((conditionTerms > 0 && actionLinked) || (evidenceTerms > 0 && conditionTerms > 0)) return 2;
        return 1;
    }

    private static List<String> entryTexts(AiAnalysisRequest request) {
        List<String> texts = new ArrayList<>();
        if (!historicalBuy(request)) {
            ScenarioPlanAiEvidence waitPlan = exactWaitPlan(request);
            if (waitPlan == null) {
                texts.add(nullToEmpty(request.thesis()));
                texts.add(nullToEmpty(request.entryReason()));
            } else {
                texts.add(nullToEmpty(waitPlan.thesis()));
                texts.add(nullToEmpty(waitPlan.entryReason()));
                addReasonTexts(request.latestActionEvidence(), texts, new ArrayList<>());
            }
        }
        addReasonTexts(request.entryActionEvidence(), texts, new ArrayList<>());
        addScenarioTexts(request.entryActionEvidence() == null ? null : request.entryActionEvidence().scenarioPlan(),
                texts, new ArrayList<>(), new ArrayList<>());
        return texts;
    }

    private static List<String> exitPlanTexts(AiAnalysisRequest request) {
        List<String> texts = new ArrayList<>();
        if (!historicalBuy(request)) {
            ScenarioPlanAiEvidence waitPlan = exactWaitPlan(request);
            texts.add(nullToEmpty(waitPlan == null ? request.exitPlan() : waitPlan.exitPlan()));
        }
        ScenarioPlanAiEvidence scenario = request.entryActionEvidence() == null
                ? null : request.entryActionEvidence().scenarioPlan();
        addScenarioTexts(scenario, new ArrayList<>(), texts, new ArrayList<>());
        return texts;
    }

    private static List<String> riskTexts(AiAnalysisRequest request) {
        List<String> texts = new ArrayList<>();
        if (!historicalBuy(request)) texts.add(nullToEmpty(request.riskNote()));
        addReasonTexts(request.entryActionEvidence(), new ArrayList<>(), texts);
        ScenarioPlanAiEvidence scenario = request.entryActionEvidence() == null
                ? null : request.entryActionEvidence().scenarioPlan();
        addScenarioTexts(scenario, new ArrayList<>(), new ArrayList<>(), texts);
        return texts;
    }

    private static List<String> precommitmentRiskTexts(AiAnalysisRequest request) {
        if (!historicalBuy(request)) {
            ScenarioPlanAiEvidence waitPlan = exactWaitPlan(request);
            return List.of(nullToEmpty(waitPlan == null ? request.riskNote() : waitPlan.riskNote()));
        }
        ScenarioPlanAiEvidence scenario = request.entryActionEvidence() == null
                ? null : request.entryActionEvidence().scenarioPlan();
        return scenario == null ? List.of() : List.of(nullToEmpty(scenario.riskNote()));
    }

    private static RiskPrecommitmentV21Analyzer.Precommitment precommitment(AiAnalysisRequest request) {
        if (!historicalBuy(request)) {
            ScenarioPlanAiEvidence waitPlan = exactWaitPlan(request);
            RiskPrecommitmentV21Analyzer.Precommitment narrative = waitPlan == null
                    ? RiskPrecommitmentV21Analyzer.classify(request.exitPlan(), request.riskNote(), null, null, false)
                    : RiskPrecommitmentV21Analyzer.classify(waitPlan);
            return RiskPrecommitmentV21Analyzer.stronger(narrative, RiskPrecommitmentV21Analyzer.classify(
                    null, null, request.stopLossPrice(), request.takeProfitPrice(), request.autoExitEnabled()));
        }
        ScenarioPlanAiEvidence exactPlan = request.entryActionEvidence() == null
                ? null : request.entryActionEvidence().scenarioPlan();
        RiskPrecommitmentV21Analyzer.Precommitment narrative =
                RiskPrecommitmentV21Analyzer.classify(exactPlan);
        if (request.lifecycleEvidence() == null
                || request.lifecycleEvidence().entryRiskRuleHistoryId() == null) return narrative;
        return RiskPrecommitmentV21Analyzer.stronger(narrative, RiskPrecommitmentV21Analyzer.classify(
                null, null, BigDecimal.ONE, null,
                request.lifecycleEvidence().historicalAutoExitEnabled()));
    }

    private static boolean historicalBuy(AiAnalysisRequest request) {
        return request.lifecycleEvidence() != null && request.lifecycleEvidence().historicalBuyExecuted();
    }

    private static ScenarioPlanAiEvidence exactWaitPlan(AiAnalysisRequest request) {
        TradeActionAiEvidence latest = request.latestActionEvidence();
        return latest != null && "WAIT".equalsIgnoreCase(latest.side()) ? latest.scenarioPlan() : null;
    }

    private static List<String> actionReasonTexts(TradeActionAiEvidence... actions) {
        List<String> texts = new ArrayList<>();
        for (TradeActionAiEvidence action : actions) {
            if (action == null) continue;
            action.reasons().forEach(reason -> {
                texts.add(reason.entryReason());
                texts.add(reason.riskNote());
            });
        }
        return texts;
    }

    private static int actionSpecificity(TradeActionAiEvidence action) {
        return action == null ? 0 : best(actionReasonTexts(action));
    }

    private static int averageActionSpecificity(TradeActionAiEvidence first, TradeActionAiEvidence second) {
        if (first == null && second == null) return 0;
        if (first != null && second != null && java.util.Objects.equals(first.tradeId(), second.tradeId())) {
            return actionSpecificity(first);
        }
        List<Integer> values = new ArrayList<>();
        if (first != null) values.add(actionSpecificity(first));
        if (second != null) values.add(actionSpecificity(second));
        return average(values);
    }

    private static int average(List<Integer> values) {
        if (values.isEmpty()) return 0;
        return (int) Math.floor(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private static void addReasonTexts(TradeActionAiEvidence action, List<String> decision, List<String> risk) {
        if (action == null) return;
        action.reasons().forEach(reason -> {
            decision.add(reason.entryReason());
            risk.add(reason.riskNote());
        });
    }

    private static void addScenarioTexts(ScenarioPlanAiEvidence scenario, List<String> entry,
                                         List<String> exit, List<String> risk) {
        if (scenario == null) return;
        entry.add(scenario.thesis());
        entry.add(scenario.entryReason());
        exit.add(scenario.exitPlan());
        risk.add(scenario.riskNote());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    record Score(int finalScore, int decisionEvidence, int planAndRisk, int execution,
                 int reviewability, int appliedCap, int entrySpecificity, int exitSpecificity,
                 int riskSpecificity, int actionReasonSpecificity) {
        String promptLine() {
            return "backendFinalScore=" + finalScore
                    + ", decisionEvidence=" + decisionEvidence + "/40"
                    + ", planAndRisk=" + planAndRisk + "/25"
                    + ", execution=" + execution + "/20"
                    + ", reviewability=" + reviewability + "/15"
                    + ", appliedCap=" + appliedCap
                    + ", evidenceSpecificity={entry:" + entrySpecificity
                    + ",exit:" + exitSpecificity + ",risk:" + riskSpecificity
                    + ",actionReason:" + actionReasonSpecificity + "}";
        }
    }
}
