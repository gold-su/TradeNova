package com.tradenova.report.dto;

import java.util.List;

/** Session-only AI contract. Chart AI continues to use {@link AiAnalysisResponse}. */
public record SessionAiAnalysisResponse(
        Integer score,
        String summary,
        List<String> warnings,
        List<String> strengths,
        DecisionReview decisionReview,
        RiskReview riskReview,
        List<BehaviorPattern> behaviorPatterns,
        List<String> nextTrainingFocus
) {
    public SessionAiAnalysisResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
        behaviorPatterns = behaviorPatterns == null ? List.of() : List.copyOf(behaviorPatterns);
        nextTrainingFocus = nextTrainingFocus == null ? List.of() : List.copyOf(nextTrainingFocus);
    }

    public record DecisionReview(String assessment, List<String> evidence, String betterAction) {
        public DecisionReview {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record RiskReview(String assessment, List<String> evidence, String improvement) {
        public RiskReview {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record BehaviorPattern(String pattern, List<String> evidence, String impact, String correction) {
        public BehaviorPattern {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }
}
