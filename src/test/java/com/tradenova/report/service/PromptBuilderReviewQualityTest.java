package com.tradenova.report.service;

import com.tradenova.report.dto.AiReviewLanguage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderReviewQualityTest {

    private final PromptBuilder promptBuilder = new PromptBuilder(
            new SessionAiDeterministicContextFormatter()
    );

    @Test
    void chartAndSessionPromptsUseTheRequestedLanguageWithoutChangingJsonKeys() {
        String chartEnglish = promptBuilder.buildSystemPrompt(AiReviewLanguage.EN);
        String chartKorean = promptBuilder.buildSystemPrompt(AiReviewLanguage.KO);
        String sessionEnglish = promptBuilder.buildSessionSystemPrompt(AiReviewLanguage.EN);
        String sessionKorean = promptBuilder.buildSessionSystemPrompt(AiReviewLanguage.KO);

        assertTrue(chartEnglish.contains("Write all user-facing review text in English"));
        assertTrue(sessionEnglish.contains("Write all user-facing review text in English"));
        assertTrue(chartKorean.contains("모든 사용자 표시용 리뷰 텍스트를 자연스러운 한국어"));
        assertTrue(sessionKorean.contains("모든 사용자 표시용 리뷰 텍스트를 자연스러운 한국어"));
        assertTrue(chartEnglish.contains("\"summary\""));
        assertTrue(sessionEnglish.contains("\"decisionReview\""));
        assertTrue(chartEnglish.contains("Keep JSON keys and enum values unchanged"));
    }

    @Test
    void chartReviewPrioritizesDecisionEvidenceWithoutChangingResponseSchema() {
        String prompt = promptBuilder.buildSystemPrompt();

        assertTrue(prompt.contains("\"score\": 0"));
        assertTrue(prompt.contains("\"summary\": \"문장\""));
        assertTrue(prompt.contains("Decision Quality, Risk / Plan Quality, Execution Quality, Learning Point"));
        assertTrue(prompt.contains("entryDecisionTechnicalContext와 entryDecisionOhlcv"));
        assertTrue(prompt.contains("profit != good decision, loss != bad decision"));
        assertTrue(prompt.contains("use only supplied evidence"));
        assertTrue(prompt.contains("동일 사실은 summary, strengths, warnings 전체에서 한 번만"));
        assertTrue(prompt.contains("raw HTML, CSS, 색상 코드를 출력하지 마라"));
        assertTrue(prompt.contains("support evidence와 판단을 약화시키는 counter-evidence"));
        assertTrue(prompt.contains("단순 사실 요약은 필요한 경우에만 최소한"));
        assertTrue(prompt.contains("Backend Risk Plan Compliance, FOLLOWED, UNKNOWN, NOT_APPLICABLE"));
        assertTrue(prompt.contains("plain text contract"));
        assertTrue(prompt.contains("Markdown 문법을 출력하지 마라"));
        assertTrue(prompt.contains("자연스러운 한국어 존댓말"));
        assertTrue(prompt.contains("historicalAutoExitEnabled, currentAutoExitEnabled, actual automatic exit"));
        assertTrue(prompt.contains("Never perform arithmetic if the result is supplied by backend facts"));
        assertTrue(prompt.contains("stop과 entry의 대소관계"));
        assertTrue(prompt.contains("structured risk rule과 narrative scenario plan은 서로 다른 evidence"));
        assertTrue(prompt.contains("손절 가격은 설정됐지만 그 근거는 충분히 기록되지 않았다"));
        assertTrue(prompt.contains("When two facts appear inconsistent, explain the scope difference"));
        assertTrue(prompt.contains("stopLossPrice, takeProfitPrice, entryRiskPlan, exitRiskPlan"));
        assertTrue(prompt.contains("이번 거래의 핵심 복기 포인트"));
        assertTrue(prompt.contains("current position state는 현재 상태만 설명"));
        assertTrue(prompt.contains("historical BUY/SELL trade와 linked event가 authoritative evidence"));
        assertTrue(prompt.contains("current positionQty=0 또는 avgPrice=0만으로 계획 이행 evidence가 부족하다고 말하지 마라"));
        assertTrue(prompt.contains("closed position 자체는 missing evidence가 아니다"));
        assertTrue(prompt.contains("backendFinalScore를 그대로 출력해라"));
        assertTrue(prompt.contains("자체적으로 재채점하지 마라"));
        assertTrue(prompt.contains("ENTRY 평가는 entry timestamp까지의 candle"));
        assertTrue(prompt.contains("exit 이후 값, session final state, 종목 공개 이후 정보"));
        assertTrue(prompt.contains("수량과 현금 잔고만으로 포지션 크기가 적절하다"));
        assertTrue(prompt.contains("risk-at-stop, account risk %, position value ratio"));
        assertTrue(prompt.contains("Presence is not quality"));
        assertTrue(prompt.contains("계획과 실행이 일치해도 약한 이유의 quality를 상향하지 마라"));
        assertTrue(prompt.contains("시간 순서만으로 그 계획 때문에 행동했다고 단정하지 마라"));
        assertTrue(prompt.contains("실제 SELL reason이 가격 하락 뒤의 반응만 말하고"));
        assertTrue(prompt.contains("시스템이 지표를 계산했다는 사실을 사용자 강점으로 만들지 마라"));
        assertTrue(prompt.contains("같은 결핍을 summary와 warnings에서 반복하지 마라"));
        assertTrue(prompt.contains("지지받고 있는 느낌"));
        assertTrue(prompt.contains("사용자 주장 자체의 구체성을 높이지 마라"));
        assertTrue(prompt.contains("사용자가 해당 지표를 직접 근거로 사용했다고 바꾸어 쓰지 마라"));
        assertTrue(prompt.contains("\"backend\", \"백엔드\", \"evidence\"라는 단어도 쓰지 말고"));
        assertTrue(prompt.contains("manual plan도 유효한 precommitment"));
        assertTrue(prompt.contains("trigger, action type, position size를 각각 비교"));
        assertTrue(prompt.contains("체결 성공, 거래 완료, 세션 완료"));
        assertTrue(prompt.contains("action 이전 exact linked plan에 없으면 사후 설명"));
        assertTrue(prompt.contains("계획 선택 관계가 저장됨"));
        assertTrue(prompt.contains("구현 설명 대신"));
    }

    @Test
    void sessionReviewSeparatesReviewAxesAndSuppressesRepeatedPlanPraise() {
        String prompt = promptBuilder.buildSessionSystemPrompt();

        assertTrue(prompt.contains("Decision Quality, Risk / Plan Quality, Execution Quality, Learning Point"));
        assertTrue(prompt.contains("계획 일관성은 하나의 평가 축일 뿐"));
        assertTrue(prompt.contains("summary, strengths, warnings, riskReview, nextTrainingFocus 전체에서 한 번만"));
        assertTrue(prompt.contains("여러 chart/trade에서 반복 확인된 패턴"));
        assertTrue(prompt.contains("use only supplied evidence"));
        assertTrue(prompt.contains("다음 훈련에서 관찰하거나 기록할 구체적인 행동 하나"));
        assertTrue(prompt.contains("raw HTML, CSS, 색상 코드를 출력하지 마라"));
        assertTrue(prompt.contains("fact recap보다 그 의미와 trade-off 해석"));
        assertTrue(prompt.contains("support evidence와 약화시킨 counter-evidence"));
        assertTrue(prompt.contains("목표 도달이나 자동 익절 성공을 Decision Quality의 근거로 사용하지 마라"));
        assertTrue(prompt.contains("Backend Risk Plan Compliance, FOLLOWED, UNKNOWN, NOT_APPLICABLE"));
        assertTrue(prompt.contains("generic 문구를 사용하지 마라"));
        assertTrue(prompt.contains("plain text contract"));
        assertTrue(prompt.contains("자연스러운 한국어 존댓말"));
        assertTrue(prompt.contains("exitRiskPlan은 청산 거래 시점의 저장된 plan snapshot"));
        assertTrue(prompt.contains("exact linked execution event로만 확인"));
        assertTrue(prompt.contains("Never perform arithmetic if the result is supplied by backend facts"));
        assertTrue(prompt.contains("structured risk rule과 narrative scenario plan을 반드시 분리"));
        assertTrue(prompt.contains("가격 기준은 설정됐지만 그 이유가 충분히 설명되지 않았다"));
        assertTrue(prompt.contains("When two facts appear inconsistent, explain the scope difference"));
        assertTrue(prompt.contains("이번 세션에서 가장 중요한 decision-quality observation 하나"));
        assertTrue(prompt.contains("실제로 수행할 수 있는 기록 또는 확인 행동"));
        assertTrue(prompt.contains("사용자 응답은 raw log를 나열하지 말고 판단 품질에 대한 해석으로 합성"));
        assertTrue(prompt.contains("snapshot 번호, trade 식별자, 내부 field 이름, backend 상태 enum"));
        assertTrue(prompt.contains("이후 가격, indicator, exit 결과, session final state를 섞지 마라"));
        assertTrue(prompt.contains("같은 execution fact를 문장만 바꿔 반복하지 마라"));
        assertTrue(prompt.contains("backendFinalScore를 그대로 출력해라"));
        assertTrue(prompt.contains("reason 존재와 reason quality를 분리"));
        assertTrue(prompt.contains("짧고 일반적인 reason을 clear, strong, technically grounded라고 표현하지 마라"));
        assertTrue(prompt.contains("이번 세션 전체에서 어떤 판단 기록과 실행 방식이 반복됐는가"));
        assertTrue(prompt.contains("plan specificity, action reason specificity, exact plan/action link"));
        assertTrue(prompt.contains("같은 특성이 독립된 action 또는 chart에서 2회 이상"));
        assertTrue(prompt.contains("section ownership"));
        assertTrue(prompt.contains("근거 부족 문구는 필요한 항목에 한 번만"));
        assertTrue(prompt.contains("Chart에서는 모호하다고 하고 Session에서는 명확하다고 뒤집지 마라"));
        assertTrue(prompt.contains("진입과 청산을 관통하는 상위 행동 특성 하나로 합성"));
        assertTrue(prompt.contains("JSON의 evidence key는 contract상 유지"));
        assertTrue(prompt.contains("자동 청산은 execution automation 방식"));
        assertTrue(prompt.contains("Session section ownership"));
        assertTrue(prompt.contains("조건형 계획이 이미 존재하면 \"구체적인 손절 가격을 쓰라\""));
        assertTrue(prompt.contains("nextTrainingFocus는 가장 중요한 한 가지 문장만"));
        assertTrue(prompt.contains("action 이전 exact linked plan에 같은 조건이 없으면 precommitment로 바꾸지 마라"));
        assertTrue(prompt.contains("exact stored relation"));
        assertTrue(prompt.contains("내부 구현 표현을 사용자-facing 문장에 출력하지 마라"));
    }
}
