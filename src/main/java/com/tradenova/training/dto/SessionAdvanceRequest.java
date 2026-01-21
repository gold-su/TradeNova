package com.tradenova.training.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

//세션을 앞으로 몇 봉(step) 진행할지 서버에 요청한다.
public record SessionAdvanceRequest(
        @Min(1) @Max(500)
        Integer steps
) { }
