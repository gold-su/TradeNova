package com.tradenova.training.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 차트의 현재 진행 상태와 계좌/포지션 스냅샷
 *
 * next/advance 응답뿐 아니라
 * 페이지 새로고침 후 상태 복구 API에서도 동일하게 사용한다.
 */
public record SessionProgressResponse(

        // 차트 식별자
        Long chartId,

        // 분석/훈련 구간 메타데이터
        Integer analysisBars,
        Integer trainingBars,
        Integer trainingProgress,
        Integer remainingTrainingBars,

        // 현재 공개된 마지막 캔들 인덱스
        Integer progressIndex,

        // 마지막 캔들 인덱스
        Integer maxIndex,

        // 앞으로 진행 가능한 봉 개수
        Integer remainingBars,

        // 현재 마지막 봉 도달 여부
        boolean atLastBar,

        // 현재 공개 봉의 종가
        BigDecimal currentPrice,

        // 차트 상태
        String chartStatus,

        // 세션 상태
        String sessionStatus,

        // 계좌/포지션 즉시 반영용 스냅샷
        BigDecimal cashBalance,
        BigDecimal positionQty,
        BigDecimal avgPrice,

        // 이번 진행에서 자동청산 발생 여부
        boolean autoExited,

        // STOP_LOSS / TAKE_PROFIT / END_OF_CHART / null
        AutoExitReason reason,

        // 이번 요청으로 실제 새롭게 공개된 캔들 (idx 오름차순)
        List<RevealedCandleResponse> revealedCandles
) {
}
