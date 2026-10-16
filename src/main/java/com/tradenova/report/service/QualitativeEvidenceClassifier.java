package com.tradenova.report.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared, deterministic claim-specificity hints for Chart and Session qualitative review. */
final class QualitativeEvidenceClassifier {

    enum Specificity {
        ABSENT,
        VAGUE,
        OBSERVATION_ONLY,
        CONDITIONED
    }

    private static final Pattern NUMBER = Pattern.compile(".*\\d.*");
    private static final Pattern VAGUE = Pattern.compile(
            ".*(그냥|느낌|같아서|것 같|많이 올|너무 올|너무 떨어|떨어질 것|오를 것|수익화).*"
    );
    private static final Pattern CONDITION = Pattern.compile(
            ".*(\\bif\\b|\\bwhen\\b|이면|라면|할 때|시 |시$|경우|유지|돌파|이탈|접근|둔화|증가|감소|종가|이상|이하).*"
    );

    private QualitativeEvidenceClassifier() {
    }

    static Specificity classify(String text) {
        if (text == null || text.isBlank()) return Specificity.ABSENT;
        String normalized = text.strip().toLowerCase(Locale.ROOT);
        if (VAGUE.matcher(normalized).matches()) return Specificity.VAGUE;
        if (NUMBER.matcher(normalized).matches() || CONDITION.matcher(normalized).matches()) {
            return Specificity.CONDITIONED;
        }
        return Specificity.OBSERVATION_ONLY;
    }

    static String meaning(String text) {
        return switch (classify(text)) {
            case ABSENT -> "사용자 작성 이유가 없음";
            case VAGUE -> "사용자 표현은 있으나 검증할 가격대·조건·구조가 없음";
            case OBSERVATION_ONLY -> "관찰은 기록됐지만 행동 조건이나 무효화 기준으로 고정되지 않음";
            case CONDITIONED -> "검증 가능한 조건 또는 가격 기준이 포함됨";
        };
    }

    static boolean isReactiveExit(String text) {
        if (text == null) return false;
        String normalized = text.strip().toLowerCase(Locale.ROOT);
        return normalized.contains("많이 올")
                || normalized.contains("너무 올")
                || normalized.contains("큰 상승")
                || normalized.contains("상승 나와")
                || normalized.contains("너무 떨어")
                || normalized.contains("많이 떨어")
                || normalized.contains("급락")
                || normalized.contains("급등");
    }
}
