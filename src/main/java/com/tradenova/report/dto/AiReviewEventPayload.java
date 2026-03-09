package com.tradenova.report.dto;


import java.util.List;

/**
 * AI 분석 결과 이벤트 Payload
 *
 * training_event 테이블 payload_json에 저장된다.
 *
 * 이벤트 타입
 * Type.AI_REVIEW
 *
 * 저장 목적
 * - 훈련 히스토리 기록
 * - 리포트 복기
 * - AI 코칭 로그
 */
public record AiReviewEventPayload(
        /**
         * AI 평가 점수
         */
        Integer score,

        /**
         * AI 요약 평가
         */
        String summary,

        /**
         * 경고 메시지 리스트
         */
        List<String> warnings,

        /**
         * 긍정적 평가 리스트
         */
        List<String> strengths

) {
}
