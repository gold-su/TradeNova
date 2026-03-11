package com.tradenova.report.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 리포트 분석 요청 DTO
 *
 * 사용 목적
 * - Snapshot 리포트를 기반으로 AI에게 트레이딩 분석을 요청할 때 사용
 * - ReportDocument + Trade + Candle 데이터를 합쳐서 생성됨
 *
 * 데이터 흐름
 * Snapshot 저장
 *  → ReportAnalysisService
 *  → AiAnalysisService
 *  → OpenAI API 호출
 *
 * 특징
 * - record 사용 (immutable DTO)
 * - Entity와 분리된 AI 전용 데이터 구조
 * - 향후 AI 분석 확장 (패턴 분석 / 전략 평가) 대비
 */
public record AiAnalysisRequest(

        /**
         * 사용자의 핵심 트레이딩 관점
         *
         * 예시
         * "거래량 증가 + 추세 전환 가능성"
         */
        String thesis,

        /**
         * 진입 근거
         *
         * 예시
         * - 이평선 정배열
         * - 거래량 돌파
         * - 지지선 반등
         */
        String entryReason,

        /**
         * 진입 근거
         *
         * 예시
         * - 이평선 정배열
         * - 거래량 돌파
         * - 지지선 반등
         */
        String exitPlan,

        /**
         * 리스크 관리 계획
         *
         * 예시
         * - 손절 3%
         * - 손절가 71000
         */
        String riskNote,

        /**
         * 체결 가격
         *
         * 현재 매수 또는 매도 가격
         */
        String freeNote,


        /**
         * 체결 가격
         *
         * 현재 매수 또는 매도 가격
         */
        BigDecimal price,
        /**
         * 거래 수량
         */
        BigDecimal qty,
        /**
         * 포지션 평균 단가
         */
        BigDecimal avgPrice,
        /**
         * 현재 보유 수량
         */
        BigDecimal positionQty,

        /**
         * 현재 남은 현금
         */
        BigDecimal cashBalance,

        /**
         * 최근 캔들 종가 리스트
         *
         * AI가 차트 흐름을 파악할 수 있도록 제공
         *
         * 예
         * [72000, 72100, 71900, 72500]
         */
        List<Double> closes,
        /**
         * 최근 캔들 종가 리스트
         *
         * AI가 차트 흐름을 파악할 수 있도록 제공
         *
         * 예
         * [72000, 72100, 71900, 72500]
         */
        List<Double> volumes
) {
}
