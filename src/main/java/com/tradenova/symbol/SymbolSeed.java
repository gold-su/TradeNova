package com.tradenova.symbol;

import com.tradenova.symbol.dto.SymbolSector;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SymbolSeed implements CommandLineRunner {

    private final SymbolRepository symbolRepository;

    @Override
    public void run(String... args){
        seed("KOSPI", SymbolSector.SEMICONDUCTOR, "005930", "삼성전자", "KRW");
        seed("KOSPI", SymbolSector.SEMICONDUCTOR, "000660", "SK하이닉스", "KRW");
        seed("KOSDAQ", SymbolSector.PLATFORM, "035720", "카카오", "KRW");
    }

    private void seed(String market, SymbolSector sector, String ticker, String name, String currency) {
        if (symbolRepository.existsByMarketAndTicker(market, ticker)) return;

        symbolRepository.save(Symbol.builder()
                .market(market)
                .ticker(ticker)
                .name(name)
                .currency(currency)
                .active(true)
                .trainingSector(sector)   // ← 이거 빠져있었음(로직 누락)
                .build());
    }
}
