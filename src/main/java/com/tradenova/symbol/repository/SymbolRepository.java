package com.tradenova.symbol.repository;

import com.tradenova.symbol.entity.Symbol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SymbolRepository extends JpaRepository<Symbol, Long> {

    //시장 + 티커로 종목 1개 조회, 유니크 제야 조건이 있으므로 최대 1건, 없을 수도 있기 때문에 optional
    Optional<Symbol> findByMarketAndTicker(String market, String ticker);

    //이미 등록된 종목인지 여부만 확인, 종목 초기 적재, 중복 insert 방지
    boolean existsByMarketAndTicker(String market, String ticker);

    //활성화된 종목 전체 조회, ID 오름차순 정렬
    List<Symbol> findAllByActiveTrueOrderByIdAsc();

    //활성 종목 중, 종목 명에 Keyword가 포함된 것, 대소문자 무시, 이름 기준 정렬, 최대 50개
    List<Symbol> findTop50ByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    //활성 종목 중, 티커에 keyword가 포함된 것, 티커 기준 정렬, 최대 50개
    List<Symbol> findTop50ByActiveTrueAndTickerContainingOrderByTickerAsc(String keyword);
}
