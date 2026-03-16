package com.tradenova.report.service;

import com.tradenova.report.dto.AiAnalysisRequest;
import org.springframework.stereotype.Component;

/**
 * AI 프롬프트 생성 전담 컴포넌트
 *
 * 역할
 * - system prompt 생성
 * - user prompt 생성
 *
 * 분리 이유
 * - AiAnalysisService는 "외부 API 호출" 책임만 가지게 하기 위해
 * - 프롬프트 수정/실험을 이 클래스에서만 하게 하기 위해
 * - 나중에 프롬프트 버전관리 하기 좋게 만들기 위해
 */
@Component
public class PromptBuilder {

    /**
     * 시스템 프롬프트 생성
     *
     * AI의 역할을 고정한다.
     * - 종목 추천 AI가 아님
     * - 사용자의 매매 판단 과정과 리스크 관리 수준을 평가하는 코치
     */
    public String buildSystemPrompt() {
        return """
                너는 트레이딩 훈련 플랫폼의 AI 코치다.
                너의 역할은 사용자의 매매 판단 과정을 평가하는 것이다.
                
                반드시 JSON 객체만 반환해라.
                아래 형식을 정확히 지켜라.
                
                {
                  "score": 0,
                  "summary": "문장",
                  "warnings": ["문장1", "문장2"],
                  "strengths": ["문장1", "문장2"]
                }
                
                규칙:
                - score는 0~100 사이 정수
                - summary는 1~3문장
                - warnings는 없으면 빈 배열
                - strengths는 없으면 빈 배열
                - 투자 추천/매수 추천 금지
                - 사용자의 리스크 관리, 진입 근거, 감정 통제, 계획 구체성을 평가해라
                - 사용자가 현저히 정보가 부족해 보이면 학습 필요성을 지적해라
                - 리포트 텍스트뿐 아니라 리스크 룰, 포지션 상태, 최근 가격 흐름도 함께 반영해라
                """;
    }

    /**
     * 유저 프롬프트 생성
     *
     * AI가 해석하기 쉽게
     * 사용자의 리포트 / 체결 / 포지션 / 리스크룰 / 차트정보를
     * 구조화된 텍스트로 만들어 전달한다.
     */
    public String buildUserPrompt(AiAnalysisRequest req) {
        return """
                [트레이딩 리포트]
                thesis: %s
                entryReason: %s
                exitPlan: %s
                riskNote: %s
                freeNote: %s
                
                [최근 체결 정보]
                price: %s
                qty: %s
                
                [현재 포지션 / 계좌]
                avgPrice: %s
                positionQty: %s
                cashBalance: %s
                
                [저장된 리스크 룰]
                stopLossPrice: %s
                takeProfitPrice: %s
                autoExitEnabled: %s
                
                [최근 종가]
                %s
                
                [최근 거래량]
                %s
                
                위 데이터를 보고 아래 항목을 평가해라:
                1) 진입 판단의 구체성
                2) 청산 계획의 명확성
                3) 리스크 관리 수준
                4) 감정적/충동적 진입 가능성
                5) 강점과 반복될 수 있는 나쁜 습관
                
                특히 아래를 중요하게 봐라:
                - 손절/익절 계획이 실제로 존재하는지
                - 리포트 내용과 실제 리스크 룰이 일관적인지
                - 진입 사유가 추상적인지 구체적인지
                - 포지션 크기와 현금 상태가 과도한지
                """.formatted(
                nullSafe(req.thesis()),
                nullSafe(req.entryReason()),
                nullSafe(req.exitPlan()),
                nullSafe(req.riskNote()),
                nullSafe(req.freeNote()),
                req.price(),
                req.qty(),
                req.avgPrice(),
                req.positionQty(),
                req.cashBalance(),
                req.stopLossPrice(),
                req.takeProfitPrice(),
                req.autoExitEnabled(),
                req.closes(),
                req.volumes()
        );
    }

    /**
     * null 방어용 문자열 헬퍼
     */
    private String nullSafe(String value) {
        return value == null ? "" : String.valueOf(value);
    }
}