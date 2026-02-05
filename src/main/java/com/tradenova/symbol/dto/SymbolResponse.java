package com.tradenova.symbol.dto;

import com.tradenova.symbol.entity.Symbol;

public record SymbolResponse(
        Long id, //내부 식별자
        String market, //마켓 구분
        String ticker, //주식 등의 코드
        String name, //종목명
        String currency, //통화
        boolean active, //거래 가능 여부
        String exchangeSector,  //공식 업종 문자열
        SymbolSector trainingSector   //훈련 섹터 enum
) {
    //from() -> 변환 책임을 DTO에 둠 / 나중에 필드 추가/제거가 쉬움
    public static SymbolResponse from(Symbol s) {
        return new SymbolResponse(
                s.getId(),
                s.getMarket(),
                s.getTicker(),
                s.getName(),
                s.getCurrency(),
                s.isActive(),
                s.getExchangeSector(),
                s.getTrainingSector()
        );
    }
}
