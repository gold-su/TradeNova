package com.tradenova.market.service;

import com.tradenova.kis.dto.CandleDto;
import com.tradenova.symbol.entity.Symbol;

import java.time.LocalDate;
import java.util.List;

/**
 * 시장 데이터 조회 인터페이스
 *
 * 목적:
 * - TrainingSessionService가 KIS에 직접 의존하지 않게 한다.
 * - 나중에 다른 데이터 공급자로 바뀌어도
 *   훈련 서비스는 이 인터페이스만 바라보면 된다.
 */
public interface MarketDataService {

    /**
     * 특정 종목의 특정 기간 일봉 조회
     *
     * 정책:
     * - DB에 충분한 데이터가 있으면 DB 데이터 반환
     * - 부족하거나 없으면 외부 API 호출 후 DB 저장 후 반환
     */
    List<CandleDto> getCandles(Symbol symbol, LocalDate from, LocalDate to, int requiredBars);
}