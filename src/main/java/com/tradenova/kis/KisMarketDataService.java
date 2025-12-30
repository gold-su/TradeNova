package com.tradenova.kis;

import com.tradenova.kis.dto.CandleDto;
import com.tradenova.kis.dto.KisItemChartPriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KisMarketDataService  {

    private final RestClient kisRestClient; // (KisRestClientConfig에서 만든 baseUrl 박힌 Bean)
    private final kisProperties props; // (appkey/appsecret/custtype/baseUrl 설정 묶음)
    private final kisTokenProvider tokenProvider; // (토큰 캐시/재발급 담당)

    /**
     * 국내주식 기간별 시세 (일/주/월/년) 조회
     * @param marketCode  J:KRX, NX:NXT, UN:통합
     * @param symbol      예: 005930
     * @param from        YYYYMMDD
     * @param to          YYYYMMDD (최대 100건 범위 권장)
     * @param period      D/W/M/Y
     * @param adjPrice    0 or 1 (문서 기준)
     */
    public List<CandleDto> getCandles(
            String marketCode,
            String symbol,
            String from,
            String to,
            String period,
            String adjPrice
    ) {
        //토큰 확보
        String accessToken = tokenProvider.getAccessToken();

        //TR ID: 문서 기준 FHKST03010100
        //KIS에서 API 호출을 구분하는 트랜잭션 ID
        String trId = "FHKST03010100";

        KisItemChartPriceResponse res = kisRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", marketCode) //시장 구분 (J/NX/UN)
                        .queryParam("FID_INPUT_ISCD", symbol) //종목코드
                        .queryParam("FID_INPUT_DATE_1", from) //시작일(from)
                        .queryParam("FID_INPUT_DATE_2", to) //종료일(to)
                        .queryParam("FID_PERIOD_DIV_CODE", period) //D/W/M/Y
                        .queryParam("FID_ORG_ADJ_PRC", adjPrice) //수정주가 여부(문서 기준)
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken) //Authorization : 토큰 없으면 호출 불가
                .header("appkey", props.appkey()) // KIS가 앱 식별
                .header("appsecret", props.appsecret()) // KIS가 앱 식별
                .header("tr_id", trId) // 어떤 거래/조회인지 식별
                .header("custtype", props.custtype()) // 개인: P
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(KisItemChartPriceResponse.class);

        if(res == null || res.output2() == null){
            throw new IllegalStateException("KIS market data response is null or missing output2");
        }
        //rtCd가 "0"이 아니면(= 성공이 아니면) 참 (true)
        if (!"0".equals(res.rtCd())){ // KIS 응답은 "HTTP 200이어도" 내부적으로 실패를 rt_cd로 줄 때가 많아서 이 체크가 매우 종요함.
            throw new IllegalStateException("KIS error: " + res.msgCd() + " / " + res.msg1());
        }

        //output2 -> CandleDto 변환
        //KIS는 문자열로 오니 parse 필요
        /**
         * KIS output2는 이렇게 생겼음 :
         * 날짜: "20251230" (문자열)
         * 가격/거래량도: "73000" (문자열)
         * TradeNova 내부에서는:
         * 시간은 epoch millis (long)
         * 가격은 double
         * 거래량은 long
         * 으로 통일하려고 CandleDto를 만들었고, 여기서 변환함.
         */
        return res.output2().stream()
                .map(row -> new CandleDto(
                        yyyymmddToEpochMillis(row.date()),
                        parseDoubleSafe(row.open()),
                        parseDoubleSafe(row.high()),
                        parseDoubleSafe(row.low()),
                        parseDoubleSafe(row.close()),
                        parseLongSafe(row.volume())
                ))
                // 보통 date 오름차순이지만, 혹시 모르니 정렬하고 싶으면 Comparator 추가
                .toList();
    }

    //날짜 변환 함수

    /**
     * "20251230" → 2025-12-30 00:00:00 (서울시간) → epoch millis
     * 프론트 차트에서 시간축 처리하기 쉬움
     **/
    private static long yyyymmddToEpochMillis(String yyyymmdd) {
        // 예: 20251230
        LocalDate d = LocalDate.of(
                Integer.parseInt(yyyymmdd.substring(0, 4)),
                Integer.parseInt(yyyymmdd.substring(4, 6)),
                Integer.parseInt(yyyymmdd.substring(6, 8))
        );
        // 한국장 기준이면 Asia/Seoul로 고정해도 됨
        return d.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
    }

    private static double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return 0.0;
        return Double.parseDouble(s.trim());
    }

    private static long parseLongSafe(String s) {
        if (s == null || s.isBlank()) return 0L;
        return Long.parseLong(s.trim());
    }
}
