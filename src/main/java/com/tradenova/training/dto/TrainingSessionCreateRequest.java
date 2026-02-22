package com.tradenova.training.dto;

import com.tradenova.training.entity.TrainingMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

//훈련 세션을 시작할 때, 프론트가 서버로 보내는 입력값 묶음
public record TrainingSessionCreateRequest(
        @NotNull Long accountId, //어떤 가상 계좌로 훈련할지 (계좌 없이 훈련은 불가능, null 이면 바로 에러)
        @NotNull TrainingMode mode, //차트에 사용할 캔들 개수
        @NotNull @Min(30) Integer bars, //bars 개수
        Integer chartCount //chart 갯수
) {
}
