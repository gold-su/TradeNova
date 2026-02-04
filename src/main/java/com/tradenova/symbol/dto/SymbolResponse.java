package com.tradenova.symbol.dto;

import com.tradenova.symbol.entity.Symbol;

public record SymbolResponse(
        Long id, //내부 식별자
        String market, //마켓 구분
        String sector, //업종
        String ticker, //주식 등의 코드
        String name, //종목명
        String currency, //통화
        boolean active //거래 가능 여부
) {
    //from() -> 변환 책임을 DTO에 둠 / 나중에 필드 추가/제거가 쉬움
    public static SymbolResponse from(Symbol s) {
        return new SymbolResponse(
                s.getId(),
                s.getMarket(),
                (s.getSector() == null ? null : s.getSector().name()),
                s.getTicker(),
                s.getName(),
                s.getCurrency(),
                s.isActive()
        );
    }
}
