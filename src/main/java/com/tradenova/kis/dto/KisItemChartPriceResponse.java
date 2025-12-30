package com.tradenova.kis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

//KIS '국내주식 기간별 시세(일/주/월)' API의 JSON 응답을 그대로 자바 record로 옮긴 DTO
public record KisItemChartPriceResponse(
        @JsonProperty("rt_cd") String rtCd, //결과 코드, 0 / 정상, 1 / 오류
        @JsonProperty("msg_cd") String msgCd, //상세 코드 (오류 유형 코드, 로그/예외 메시지 매핑에 사용
        @JsonProperty("msg1") String msg1, //디버깅,로그 출력용 / "정상 처리되었습니다." 같은 사람이 읽는 메시지
        @JsonProperty("output2") List<KisCandleRow> output2 //일봉 / 주봉/ 월봉 데이터 배열, 한 개 = 캔들 하나
) {
    public record KisCandleRow(
            @JsonProperty("stck_bsop_date") String date,  // YYYYMMDD / 거래일
            @JsonProperty("stck_oprc") String open, // 시가
            @JsonProperty("stck_hgpr") String high, // 고가
            @JsonProperty("stck_lwpr") String low, // 저가
            @JsonProperty("stck_clpr") String close, // 중가
            @JsonProperty("acml_vol") String volume // 누적 거래량
    ) {}
}
