package com.tradenova.market.service;

import com.tradenova.kis.dto.CandleDto;
import com.tradenova.kis.service.KisMarketDataService;
import com.tradenova.market.entity.MarketCandle;
import com.tradenova.market.repository.MarketCandleRepository;
import com.tradenova.symbol.entity.Symbol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 캐시 우선 시장 데이터 서비스
 *
 * 흐름:
 * 1. DB에서 먼저 조회
 * 2. 데이터가 있으면 DB 데이터 반환
 * 3. 데이터가 없으면 KIS 호출
 * 4. 받아온 데이터를 DB에 저장
 * 5. 저장 후 다시 DB 기준으로 반환
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachedMarketDataService implements MarketDataService {

    /**
     * KIS API 요청 파라미터 설정

     * MARKET_CODE: 시장 코드 (J = 국내 주식)
     * PERIOD: 조회 주기 (D = 일봉)
     * ADJ_PRICE: 수정주가 여부 (0 = 기본값)
     * KIS_DATE: 날짜 포맷 (yyyyMMdd)
     */
    private static final String MARKET_CODE = "J";
    private static final String PERIOD = "D";
    private static final String ADJ_PRICE = "0";
    private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    // 데이터 저장/조회용 Repo
    private final MarketCandleRepository marketCandleRepository;
    // KIS API 호출 서비스
    private final KisMarketDataService kisMarketDataService;

    @Override
    public List<CandleDto> getCandles(Symbol symbol, LocalDate from, LocalDate to, int requiredBars) {

        // 1) DB에서 먼저 해당 종목 + 기간의 캔들 조회 (오름차순)
        List<MarketCandle> cached = marketCandleRepository
                .findAllBySymbol_IdAndCandleDateBetweenOrderByCandleDateAsc(
                        symbol.getId(),
                        from,
                        to
                );

        // 2) DB 데이터 개수가 필요한 개수(requiredBars) 이상이면 캐시 히트
        // -> API 호출 없이 바로 반환
        if (cached.size() >= requiredBars) {
            log.info("market candle cache hit. symbol={}, from={}, to={}, size={}",
                    symbol.getTicker(), from, to, cached.size());

            // DB 엔티티 -> DTO 변환 후 반환
            return cached.stream()
                    .map(this::toCandleDto)
                    .toList();
        }

        // 3) DB 데이터가 부족하면 캐시 미스 -> KIS API 호출
        log.info("market candle cache insufficient. call KIS. symbol={}, from={}, to={}, cachedSize={}, requiredBars={}",
                symbol.getTicker(), from, to, cached.size(), requiredBars);

        // 4) 외부 KIS API에서 캔들 데이터 조회
        List<CandleDto> apiCandles = kisMarketDataService.getCandles(
                MARKET_CODE,            // 시장 코드 (예: 국내 주식)
                symbol.getTicker(),     // 종목 코드
                from.format(KIS_DATE),  // 시작 날짜 (yyyyMMdd)
                to.format(KIS_DATE),    // 종료 날짜
                PERIOD,                 // 주기 (일봉)
                ADJ_PRICE               // 수정주가 여부
        );

        // 5) API에서 받은 데이터를 DB에 저장 (중복 방지 포함)
        saveIfAbsent(symbol, apiCandles);

        // 6) 저장 후 다시 DB에서 조회 (정합성 확보)
        List<MarketCandle> stored = marketCandleRepository
                .findAllBySymbol_IdAndCandleDateBetweenOrderByCandleDateAsc(
                        symbol.getId(),
                        from,
                        to
                );

        // 7) 최종적으로 DB 기준 데이터를 DTO로 변환해서 반환
        return stored.stream()
                .map(this::toCandleDto)
                .toList();
    }

    /**
     * API 응답을 market_candle 테이블에 저장
     *
     * 중복 방지:
     * - symbol_id + candle_date 기준 exists 체크
     */
    private void saveIfAbsent(Symbol symbol, List<CandleDto> candles) {

        // candles가 null이거나 비어있으면 아무것도 하지 않고 종료
        if (candles == null || candles.isEmpty()) {
            return;
        }

        // DB에 저장할 MarketCandle 리스트 (배치 저장용)
        List<MarketCandle> toSave = new ArrayList<>();

        // API에서 받은 candle 데이터를 하나씩 순회
        for (CandleDto c : candles) {

            // epochMillis(timestamp)를 서울 기준 날짜(LocalDate)로 변환
            LocalDate candleDate = epochMillisToSeoulDate(c.t());

            // 이미 해당 종목 + 날짜 데이터가 DB에 존재하는지 확인
            boolean exists = marketCandleRepository.existsBySymbol_IdAndCandleDate(
                    symbol.getId(),
                    candleDate
            );

            // 이미 존재하면 저장하지 않고 다음 데이터로 넘어감
            if (exists) {
                continue;
            }

            // 존재하지 않는 데이터만 MarketCandle 엔티티로 변환하여 리스트에 추가
            toSave.add(
                    MarketCandle.builder()
                            .symbol(symbol)         // 종목 정보
                            .candleDate(candleDate) // 날짜
                            .openPrice(c.o())       // 시가
                            .highPrice(c.h())       // 고가
                            .lowPrice(c.l())        // 저가
                            .closePrice(c.c())      // 종가
                            .volume((long) c.v())   // 거래량 (double -> long 캐스팅)
                            .build()
            );
        }
        // 저장할 데이터가 하나라도 있을 경우에만 DB 저장 수행
        if (!toSave.isEmpty()) {

            // 여러 건을 한 번에 저장 (성능 최적화)
            marketCandleRepository.saveAll(toSave);

            // 저장 로그 출력 (디버깅 및 모니터링용)
            log.info("saved market candles. symbol={}, savedCount={}",
                    symbol.getTicker(), toSave.size());
        }
    }

    /**
     * MarketCandle -> CandleDto 변환
     */
    private CandleDto toCandleDto(MarketCandle m) {

        // LocalDate를 서울 기준 자정으로 변환 후 epochMillis로 변환
        long epochMillis = m.getCandleDate()
                .atStartOfDay(ZoneId.of("Asia/Seoul")) // 해당 날짜의 00:00:00 (한국 시간)
                .toInstant()                          // Instant로 변환
                .toEpochMilli();                      // 밀리초 단위 timestamp로 변환


        // CandleDto 객체 생성 (프론트에서 사용하는 형태)
        return new CandleDto(
                epochMillis,        // 시간 (timestamp)
                m.getOpenPrice(),   // 시가
                m.getHighPrice(),   // 고가
                m.getLowPrice(),    // 저가
                m.getClosePrice(),  // 종가
                m.getVolume()       // 거래량
        );
    }

    /**
     * epoch millis -> 서울 기준 LocalDate 변환
     */
    private LocalDate epochMillisToSeoulDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis) // timestamp -> Instant 변환
                .atZone(ZoneId.of("Asia/Seoul")) // 한국 시간 기준으로 변환
                .toLocalDate();                         // 날짜(LocalDate)만 추출
    }
}