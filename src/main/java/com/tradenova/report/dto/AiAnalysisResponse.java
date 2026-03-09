package com.tradenova.report.dto;


import java.util.List;

/**
 * AI 리포트 분석 결과 DTO
 *
 * OpenAI 분석 결과를 구조화한 데이터
 *
 * 사용 흐름
 * OpenAI 응답
 *  → JSON 파싱
 *  → AiAnalysisResponse 생성
 *  → training_event payload로 저장
 */
public record AiAnalysisResponse(

        /**
         * 트레이딩 평가 점수
         *
         * 범위
         * 0 ~ 100
         *
         * 기준
         * - 리스크 관리
         * - 진입 근거
         * - 감정 통제
         */
        Integer score,

        /**
         * AI 요약 평가
         *
         * 예시
         * "거래량 기반 진입은 좋았지만
         * 손절 계획이 부족합니다."
         */
        String summary,

        /**
         * AI 경고 목록
         *
         * 예시
         * - 손절 기준 없음
         * - 추격매수 가능성
         */
        List<String> warnings,

        /**
         * 긍정적 요소
         *
         * 예시
         * - 거래량 돌파 포착
         * - 추세 방향 진입
         */
        List<String> strengths

) {}