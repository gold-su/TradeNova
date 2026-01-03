package com.tradenova.paper.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

//가상계좌 조회 할 때 응답
public record PaperAccountResponse(
        Long id, //계좌 고유 ID
        String name, //이름
        String description, //계좌 설명
        BigDecimal initialBalance, //계좌 생성 시 초기 자본금
        BigDecimal cashBalance, //햔재 사용 가능한 현금
        BaseCurrency baseCurrency, //기준 통화 (KRW / USD 등)
        boolean isDefault, //기존 계좌 여부
        OffsetDateTime createdAt //계좌 생성 시각
) {
}
