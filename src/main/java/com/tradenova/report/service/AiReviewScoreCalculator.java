package com.tradenova.report.service;

import com.tradenova.report.dto.*;

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

    private AiReviewScoreCalculator() {
    }

    static Score score(AiAnalysisRequest request) {
        int entry = best(entryTexts(request));
        int exit = best(exitTexts(request));
        int risk = best(riskTexts(request));

        int decision = decisionScore(entry, exit);
        int plan = planScore(
                request.stopLossPrice() != null,
                request.takeProfitPrice() != null,
                Boolean.TRUE.equals(request.autoExitEnabled()),
                risk,
                exit
        );
        boolean buy = request.lifecycleEvidence() != null && request.lifecycleEvidence().historicalBuyExecuted();
        boolean sell = request.lifecycleEvidence() != null
                && "SELL".equals(request.lifecycleEvidence().latestTradeSide());
        int actionReason = best(actionReasonTexts(request.entryActionEvidence(), request.latestActionEvidence()));
        int execution = executionScore(buy, sell, actionReason, false);
        int reviewability = reviewabilityScore(entry, exit, risk);
        return finish(decision, plan, execution, reviewability, entry, exit, risk, buy || sell, actionReason);
    }

    static Score score(SessionAiAnalysisRequest request) {
        List<String> entryTexts = new ArrayList<>();
        List<String> exitTexts = new ArrayList<>();
        List<String> riskTexts = new ArrayList<>();
        if (request.snapshots() != null) {
            request.snapshots().forEach(snapshot -> {
                entryTexts.add(snapshot.thesis());
                entryTexts.add(snapshot.entryReason());
                exitTexts.add(snapshot.exitPlan());
                riskTexts.add(snapshot.riskNote());
            });
        }

        boolean buy = false;
        boolean sell = false;
        int actionReason = 0;
        boolean stop = false;
        boolean target = false;
        boolean autoExit = false;
        boolean followed = false;
        if (request.qualitativeEvidenceContext() != null) {
            for (ChartQualitativeEvidenceContext chart : request.qualitativeEvidenceContext().charts()) {
                for (TradeActionAiEvidence action : chart.tradeActions()) {
                    buy |= "BUY".equals(action.side());
                    sell |= "SELL".equals(action.side());
                    actionReason = Math.max(actionReason, best(actionReasonTexts(action)));
                    addScenarioTexts(action.scenarioPlan(), entryTexts, exitTexts, riskTexts);
                    if ("BUY".equals(action.side())) addReasonTexts(action, entryTexts, riskTexts);
                    if ("SELL".equals(action.side())) addReasonTexts(action, exitTexts, riskTexts);
                }
            }
            for (RiskComplianceAiEvidence evidence : request.qualitativeEvidenceContext().riskCompliance()) {
                stop |= Boolean.TRUE.equals(evidence.stopLossConfigured());
                target |= Boolean.TRUE.equals(evidence.takeProfitConfigured());
                autoExit |= Boolean.TRUE.equals(evidence.autoExitEnabledAtTrade());
                followed |= "FOLLOWED".equals(evidence.compliance());
            }
        }

        int entry = best(entryTexts);
        int exit = best(exitTexts);
        int risk = best(riskTexts);
        return finish(
                decisionScore(entry, exit),
                planScore(stop, target, autoExit, risk, exit),
                executionScore(buy, sell, actionReason, followed),
                reviewabilityScore(entry, exit, risk),
                entry, exit, risk, buy || sell, actionReason
        );
    }

    private static Score finish(int decision, int plan, int execution, int reviewability,
                                int entrySpecificity, int exitSpecificity, int riskSpecificity,
                                boolean hasExecutedAction, int actionReasonSpecificity) {
        int raw = decision + plan + execution + reviewability;
        int cap = 100;
        if (entrySpecificity <= 1 && exitSpecificity <= 1) cap = Math.min(cap, 59);
        if (entrySpecificity <= 2 && exitSpecificity <= 2) cap = Math.min(cap, 79);
        if (entrySpecificity <= 1 && riskSpecificity <= 1) cap = Math.min(cap, 69);
        if (hasExecutedAction && actionReasonSpecificity == 0) cap = Math.min(cap, 79);
        return new Score(Math.min(raw, cap), decision, plan, execution, reviewability, cap);
    }

    private static int decisionScore(int entry, int exit) {
        return points(entry, 0, 10, 18, 25) + points(exit, 0, 5, 10, 15);
    }

    private static int planScore(boolean stop, boolean target, boolean autoExit, int risk, int exit) {
        int score = (stop ? 3 : 0) + (target ? 3 : 0) + (autoExit ? 2 : 0);
        score += points(risk, 0, 5, 10, 14);
        score += points(exit, 0, 1, 2, 3);
        return Math.min(25, score);
    }

    private static int executionScore(boolean buy, boolean sell, int actionReason, boolean followed) {
        int score = (buy ? 4 : 0) + (sell ? 4 : 0) + points(actionReason, 0, 3, 6, 9);
        if (followed) score += 3;
        return Math.min(20, score);
    }

    private static int reviewabilityScore(int entry, int exit, int risk) {
        return Math.min(15, entry * 2 + exit * 2 + risk);
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
        if (vague || text.length() < 12) return 1;
        if (NUMBER.matcher(text).matches() || (text.length() >= 25 && evidenceTerms >= 3)) return 3;
        if (evidenceTerms >= 2 || text.length() >= 20) return 2;
        return 1;
    }

    private static List<String> entryTexts(AiAnalysisRequest request) {
        List<String> texts = new ArrayList<>(List.of(nullToEmpty(request.thesis()), nullToEmpty(request.entryReason())));
        addReasonTexts(request.entryActionEvidence(), texts, new ArrayList<>());
        addScenarioTexts(request.entryActionEvidence() == null ? null : request.entryActionEvidence().scenarioPlan(),
                texts, new ArrayList<>(), new ArrayList<>());
        return texts;
    }

    private static List<String> exitTexts(AiAnalysisRequest request) {
        List<String> texts = new ArrayList<>(List.of(nullToEmpty(request.exitPlan())));
        if (request.latestActionEvidence() != null && "SELL".equals(request.latestActionEvidence().side())) {
            addReasonTexts(request.latestActionEvidence(), texts, new ArrayList<>());
        }
        return texts;
    }

    private static List<String> riskTexts(AiAnalysisRequest request) {
        List<String> texts = new ArrayList<>(List.of(nullToEmpty(request.riskNote())));
        addReasonTexts(request.entryActionEvidence(), new ArrayList<>(), texts);
        ScenarioPlanAiEvidence scenario = request.entryActionEvidence() == null
                ? null : request.entryActionEvidence().scenarioPlan();
        addScenarioTexts(scenario, new ArrayList<>(), new ArrayList<>(), texts);
        return texts;
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
                 int reviewability, int appliedCap) {
        String promptLine() {
            return "backendFinalScore=" + finalScore
                    + ", decisionEvidence=" + decisionEvidence + "/40"
                    + ", planAndRisk=" + planAndRisk + "/25"
                    + ", execution=" + execution + "/20"
                    + ", reviewability=" + reviewability + "/15"
                    + ", appliedCap=" + appliedCap;
        }
    }
}
