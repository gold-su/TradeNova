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
        // ===== 반도체 =====
        seed("KOSPI", SymbolSector.SEMICONDUCTOR, "005930", "삼성전자", "KRW");
        seed("KOSPI", SymbolSector.SEMICONDUCTOR, "000660", "SK하이닉스", "KRW");

        // ===== 2차전지 =====
        seed("KOSPI", SymbolSector.SECONDARY_BATTERY, "373220", "LG에너지솔루션", "KRW");
        seed("KOSDAQ", SymbolSector.SECONDARY_BATTERY, "247540", "에코프로비엠", "KRW");

        // ===== 플랫폼 / 인터넷 =====
        seed("KOSPI", SymbolSector.PLATFORM, "035420", "NAVER", "KRW");
        seed("KOSPI", SymbolSector.PLATFORM, "035720", "카카오", "KRW");

        // ===== 바이오 =====
        seed("KOSPI", SymbolSector.BIO, "068270", "셀트리온", "KRW");

        // ===== 금융 =====
        seed("KOSPI", SymbolSector.FINANCE, "105560", "KB금융", "KRW");

        // ===== 방산 =====
        seed("KOSPI", SymbolSector.DEFENSE, "012450", "한화에어로스페이스", "KRW");

        // ===== 조선 =====
        seed("KOSPI", SymbolSector.SHIPBUILDING, "009540", "한국조선해양", "KRW");
    }

    private void seed(String market, SymbolSector sector, String ticker, String name, String currency) {
        if (symbolRepository.existsByMarketAndTicker(market, ticker)) return;

        symbolRepository.save(Symbol.builder()
                .market(market)
                .ticker(ticker)
                .name(name)
                .currency(currency)
                .active(true)
                .trainingSector(sector)
                .build());
    }
}
