package com.tradenova.report.service;

import com.tradenova.report.dto.AiAnalysisRequest;
import com.tradenova.report.dto.SessionAiAnalysisRequest;
import com.tradenova.report.dto.SessionChartSummary;
import com.tradenova.report.dto.SessionSnapshotSummary;
import org.springframework.stereotype.Component;

import java.util.List;

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
     * 세션 단위 AI 평가용 시스템 프롬포트 생성
     */
    public String buildSessionSystemPrompt() {
        return """
        너는 트레이딩 훈련 플랫폼의 AI 코치다.
        너의 역할은 "한 개 차트"가 아니라 "한 세션 전체"를 평가하는 것이다.

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

        평가 기준:
        - 세션 전체의 선택, 관망, 자금 사용, 계획 일관성을 평가해라
        - 거래한 차트뿐 아니라 거래하지 않은 차트를 어떻게 다뤘는지도 평가해라
        - 여러 차트 중 어떤 차트를 선택했고 어떤 차트를 무시했는지 반드시 평가하라
        - 수익/손실이 발생한 차트뿐 아니라, 기회였던 차트를 놓친 경우도 평가하라

        reasoning:
        - snapshot이 존재하면 reasoning의 일관성을 반영해라
        - snapshot이 없더라도 이벤트/거래/차트 상태만으로 최대한 평가해라
        - 실제 거래 여부와 계획이 얼마나 일치하는지 중요하게 보라

        PnL 해석:
        - finalPnL은 참고 지표로만 사용하고 결과만으로 판단하지 마라
        - 동일한 행동이라도 finalPnL 결과에 따라 평가를 조정하되,
          결과보다 판단 과정과 일관성을 더 중요하게 평가하라
        """;
    }

    /**
     * 세션 데이터를 기반으로 AI에게 전달할 사용자 프롬포트 생성
     */
    public String buildSessionUserPrompt(SessionAiAnalysisRequest req) {
        return """
            [세션 기본 정보]
            sessionId: %s
            accountId: %s
            mode: %s
            sessionStatus: %s
            totalChartCount: %s
            completedChartCount: %s
            totalTradeCount: %s
            totalEventCount: %s

            [차트 요약]
            %s

            [세션 스냅샷 요약]
            %s

            위 데이터를 보고 아래 항목을 평가해라:
            1) 여러 차트 중 선택과 관망의 적절성
            2) 세션 전체의 계획 일관성
            3) 거래 과잉/거래 회피 경향
            4) reasoning 품질과 반복 습관
            5) 다음 세션에서 개선할 점

            특히 아래를 중요하게 봐라:
            - 거래가 특정 차트에만 몰렸는지
            - snapshot 내용들이 서로 일관적인지
            - 실제 거래 여부와 계획 메모가 얼마나 연결되는지
            - 세션 전체적으로 충동성이 있었는지
            """.formatted(
                req.sessionId(),
                req.accountId(),
                req.mode(),
                req.sessionStatus(),
                req.totalChartCount(),
                req.completedChartCount(),
                req.totalTradeCount(),
                req.totalEventCount(),
                chartBlock(req.charts()),
                snapshotBlock(req.snapshots())
        );
    }

    // 세션 내 모든 차트의 요약 정보를 문자열로 변환
    private String chartBlock(List<SessionChartSummary> charts) {
        if (charts == null || charts.isEmpty()) {
            return "없음";
        }

        StringBuilder sb = new StringBuilder();
        for (SessionChartSummary c : charts) {
            sb.append("- chartId=").append(c.chartId())
                    .append(", chartIndex=").append(c.chartIndex())
                    .append(", symbol=").append(c.symbolTicker()).append(" / ").append(c.symbolName())
                    .append(", status=").append(c.status())
                    .append(", progressIndex=").append(c.progressIndex())
                    .append(", tradeCount=").append(c.tradeCount())
                    .append(", eventCount=").append(c.eventCount())
                    .append(", snapshotCount=").append(c.snapshotCount())
                    .append(", traded=").append(c.traded())
                    .append(", finalPnL=").append(c.finalPnL())
                    .append("\n");
        }
        return sb.toString();
    }

    // 세션 내 snapshot(사용자 분석 기록) 요약 생성
    private String snapshotBlock(List<SessionSnapshotSummary> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "세션 내 저장된 snapshot 없음";
        }

        StringBuilder sb = new StringBuilder();
        for (SessionSnapshotSummary s : snapshots) {
            sb.append("- chartId=").append(s.chartId())
                    .append(", version=").append(s.version())
                    .append(", thesis=").append(nullSafe(s.thesis()))
                    .append(", entryReason=").append(nullSafe(s.entryReason()))
                    .append(", exitPlan=").append(nullSafe(s.exitPlan()))
                    .append(", riskNote=").append(nullSafe(s.riskNote()))
                    .append(", freeNote=").append(nullSafe(s.freeNote()))
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * null 방어용 문자열 헬퍼
     */
    private String nullSafe(String value) {
        return value == null ? "" : String.valueOf(value);
    }
}