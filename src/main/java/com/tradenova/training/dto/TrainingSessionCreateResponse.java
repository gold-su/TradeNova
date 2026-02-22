package com.tradenova.training.dto;

import com.tradenova.training.entity.TrainingMode;
import com.tradenova.training.entity.TrainingStatus;

import java.time.LocalDate;
import java.util.List;


/**
 * 훈련 세션 생성 직후 프론트엔드에 내려주는 응답 DTO
 *
 * 목적:
 * - 프론트가 "세션 생성 → 즉시 차트 화면 구성"을 할 수 있도록
 *   필요한 모든 정보를 한 번에 제공
 * - 이후 훈련 관련 API의 기준 키는 sessionId
 */
//훈련 세션을 생성한 직후, 프론트가 바로 화면을 구성할 수 있도록 필요한 정보를 한 번에 내려주는 응답 객체
public record TrainingSessionCreateResponse(
        Long sessionId, //생성된 TrainingSession의 PK “훈련 진행/캔들/거래 등 차트 단위 API의 기준 키는 chartId"
        Long accountId, //어떤 가상계좌로 훈련 중인지
        TrainingMode mode, //훈련 방식
        TrainingStatus status, //세션 상태
        List<TrainingChartCreateResponse> charts //멀티 차트 리스트로 반환
) {
}
