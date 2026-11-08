package com.tradenova.market.repository;

import com.tradenova.market.entity.MarketCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 원천 시세(market_candle) 조회/저장 Repository
 *
 * 역할 :
 * - 특정 종목의 기간별 캔들 조회
 * - 중복 여부 확인
 */
public interface MarketCandleRepository extends JpaRepository<MarketCandle, Long> {

    @Query("""
            select candle.symbol.id
            from MarketCandle candle
            where candle.symbol.active = true
            group by candle.symbol.id
            having count(candle.id) >= :requiredBars
            order by candle.symbol.id
            """)
    List<Long> findEligibleActiveSymbolIds(@Param("requiredBars") long requiredBars);

    List<MarketCandle> findAllBySymbol_IdOrderByCandleDateAsc(Long symbolId);

    /**
     * 특정 종목의 특정 기간 일봉 조회
     *
     * 예:
     * 삼성전자 2020-01-01 ~ 2020-12-31
     */
    List<MarketCandle> findAllBySymbol_IdAndCandleDateBetweenOrderByCandleDateAsc(
            Long symbolId,
            LocalDate from,
            LocalDate to
    );

    /**
     * 특정 종목 + 날짜 데이터 존재 여부 확인
     *
     * 중복 저장 방지용
     */
    boolean existsBySymbol_IdAndCandleDate(
            Long symbolId,
            LocalDate candleDate
    );
}
