package com.tradenova.symbol.repository;

import com.tradenova.symbol.dto.SymbolSector;
import com.tradenova.symbol.entity.Symbol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SymbolRepository extends JpaRepository<Symbol, Long> {

    /**
     * (market, ticker)로 단건 조회
     * - DB 유니크 제약(uk_symbol_market_ticker)과 짝
     * - 없을 수 있으니 Optional
     */
    Optional<Symbol> findByMarketAndTicker(String market, String ticker);

    /**
     * (market, ticker) 존재 여부만 빠르게 확인
     * - Seed/초기 적재 시 중복 insert 방지용
     */
    boolean existsByMarketAndTicker(String market, String ticker);

    /**
     * 활성(active=true) 종목 전체 조회 (id 오름차순)
     */
    List<Symbol> findAllByActiveTrueOrderByIdAsc();

    /**
     * 활성 종목 중 "이름"에 keyword 포함 (대소문자 무시), name 오름차순, 최대 50개
     */
    List<Symbol> findTop50ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    /**
     * 활성 종목 중 "티커"에 keyword 포함, ticker 오름차순, 최대 50개
     */
    List<Symbol> findTop50ByActiveTrueAndTickerContainingOrderByTickerAsc(String keyword);

    // =========================
    // Training Sector (내부 분류) 기반 필터링
    // =========================

    /**
     * 활성 종목 중 trainingSector 일치하는 것만 (id 오름차순)
     */
    List<Symbol> findAllByActiveTrueAndTrainingSectorOrderByIdAsc(SymbolSector trainingSector);

    /**
     * 활성 + trainingSector 일치 + name에 keyword 포함 (대소문자 무시), name 오름차순, 최대 50개
     */
    List<Symbol> findTop50ByActiveTrueAndTrainingSectorAndNameContainingIgnoreCaseOrderByNameAsc(
            SymbolSector trainingSector, String keyword
    );

    /**
     * 활성 + trainingSector 일치 + ticker에 keyword 포함, ticker 오름차순, 최대 50개
     */
    List<Symbol> findTop50ByActiveTrueAndTrainingSectorAndTickerContainingOrderByTickerAsc(
            SymbolSector trainingSector, String keyword
    );

    // =========================
    // Exchange Sector (거래소 업종 문자열) 기반 필터링
    // =========================

    /**
     * 활성 종목 중 exchangeSector 정확히 일치하는 것만 (id 오름차순)
     * - exchangeSector는 MVP에서 수동 입력/빈값 가능이라 null/"" 처리 주의
     */
    List<Symbol> findAllByActiveTrueAndExchangeSectorOrderByIdAsc(String exchangeSector);

    /**
     * 활성 + exchangeSector 정확히 일치 + name에 keyword 포함 (대소문자 무시), name 오름차순, 최대 50개
     */
    List<Symbol> findTop50ByActiveTrueAndExchangeSectorAndNameContainingIgnoreCaseOrderByNameAsc(
            String exchangeSector, String keyword
    );

    /**
     * 활성 + exchangeSector 정확히 일치 + ticker에 keyword 포함, ticker 오름차순, 최대 50개
     */
    List<Symbol> findTop50ByActiveTrueAndExchangeSectorAndTickerContainingOrderByTickerAsc(
            String exchangeSector, String keyword
    );

    // =========================
    // Training + Exchange 동시 필터링 (둘 다 해당하는 종목만)
    // =========================

    /**
     * 활성 + trainingSector + exchangeSector 모두 일치 (id 오름차순)
     */
    List<Symbol> findAllByActiveTrueAndTrainingSectorAndExchangeSectorOrderByIdAsc(
            SymbolSector trainingSector, String exchangeSector
    );

    /**
     * 활성 + trainingSector + exchangeSector 일치 + name 검색 (대소문자 무시), name 오름차순, 최대 50개
     */
    List<Symbol> findTop50ByActiveTrueAndTrainingSectorAndExchangeSectorAndNameContainingIgnoreCaseOrderByNameAsc(
            SymbolSector trainingSector, String exchangeSector, String keyword
    );

    /**
     * 활성 + trainingSector + exchangeSector 일치 + ticker 검색, ticker 오름차순, 최대 50개
     */
    List<Symbol> findTop50ByActiveTrueAndTrainingSectorAndExchangeSectorAndTickerContainingOrderByTickerAsc(
            SymbolSector trainingSector, String exchangeSector, String keyword
    );
}
