package com.tradenova.paper.dto;

import java.math.BigDecimal;

//가상계좌 새로 생성 요청
public record PaperAccountCreateRequest(
        String name, //이름
        String description, // 계좌 설명
        BigDecimal initialBalance //계좌 생성 시 초기 자본금
)
{ }
