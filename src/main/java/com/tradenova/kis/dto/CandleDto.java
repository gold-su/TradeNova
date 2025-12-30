package com.tradenova.kis.dto;

//정규화된 캔들 데이터 형식 DTO
//백테스트, 지표 계산, 수익률 계산 등을 하기 위함
public record CandleDto(
        long t,    // epoch millis (또는 YYYYMMDD를 ms로), time
        double o, // Open
        double h, // High
        double l, // Low
        double c, // Close
        long v // Volume(거래량)
) {
}
