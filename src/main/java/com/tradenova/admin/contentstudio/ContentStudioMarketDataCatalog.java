package com.tradenova.admin.contentstudio;

import com.tradenova.symbol.dto.SymbolSector;

import java.util.List;

final class ContentStudioMarketDataCatalog {

    private ContentStudioMarketDataCatalog() {
    }

    static final List<TargetSymbol> TARGETS = List.of(
            target("KOSPI", "000100", "유한양행", SymbolSector.BIO, "의약품"),
            target("KOSPI", "000270", "기아", SymbolSector.ETC, "자동차"),
            target("KOSPI", "000660", "SK하이닉스", SymbolSector.SEMICONDUCTOR, "반도체"),
            target("KOSPI", "005380", "현대차", SymbolSector.ETC, "자동차"),
            target("KOSPI", "005490", "POSCO홀딩스", SymbolSector.ETC, "철강"),
            target("KOSPI", "005930", "삼성전자", SymbolSector.SEMICONDUCTOR, "반도체"),
            target("KOSPI", "006400", "삼성SDI", SymbolSector.SECONDARY_BATTERY, "전기전자"),
            target("KOSPI", "009540", "HD한국조선해양", SymbolSector.SHIPBUILDING, "조선"),
            target("KOSPI", "010140", "삼성중공업", SymbolSector.SHIPBUILDING, "조선"),
            target("KOSPI", "012450", "한화에어로스페이스", SymbolSector.DEFENSE, "방산"),
            target("KOSPI", "034020", "두산에너빌리티", SymbolSector.ETC, "기계"),
            target("KOSPI", "035420", "NAVER", SymbolSector.PLATFORM, "서비스"),
            target("KOSPI", "035720", "카카오", SymbolSector.PLATFORM, "서비스"),
            target("KOSPI", "042660", "한화오션", SymbolSector.SHIPBUILDING, "조선"),
            target("KOSPI", "042700", "한미반도체", SymbolSector.SEMICONDUCTOR, "반도체"),
            target("KOSPI", "047810", "한국항공우주", SymbolSector.DEFENSE, "방산"),
            target("KOSPI", "051910", "LG화학", SymbolSector.SECONDARY_BATTERY, "화학"),
            target("KOSPI", "055550", "신한지주", SymbolSector.FINANCE, "금융"),
            target("KOSPI", "064350", "현대로템", SymbolSector.DEFENSE, "기계"),
            target("KOSPI", "066570", "LG전자", SymbolSector.ETC, "전기전자"),
            target("KOSPI", "068270", "셀트리온", SymbolSector.BIO, "의약품"),
            target("KOSPI", "079550", "LIG넥스원", SymbolSector.DEFENSE, "방산"),
            target("KOSPI", "086790", "하나금융지주", SymbolSector.FINANCE, "금융"),
            target("KOSPI", "105560", "KB금융", SymbolSector.FINANCE, "금융"),
            target("KOSPI", "138040", "메리츠금융지주", SymbolSector.FINANCE, "금융"),
            target("KOSPI", "207940", "삼성바이오로직스", SymbolSector.BIO, "의약품"),
            target("KOSPI", "259960", "크래프톤", SymbolSector.ETC, "게임"),
            target("KOSPI", "267250", "HD현대", SymbolSector.ETC, "지주"),
            target("KOSPI", "272210", "한화시스템", SymbolSector.DEFENSE, "방산"),
            target("KOSPI", "316140", "우리금융지주", SymbolSector.FINANCE, "금융"),
            target("KOSPI", "329180", "HD현대중공업", SymbolSector.SHIPBUILDING, "조선"),
            target("KOSPI", "373220", "LG에너지솔루션", SymbolSector.SECONDARY_BATTERY, "전기전자"),
            target("KOSDAQ", "028300", "HLB", SymbolSector.BIO, "제약·바이오"),
            target("KOSDAQ", "058470", "리노공업", SymbolSector.SEMICONDUCTOR, "반도체"),
            target("KOSDAQ", "086520", "에코프로", SymbolSector.SECONDARY_BATTERY, "2차전지"),
            target("KOSDAQ", "196170", "알테오젠", SymbolSector.BIO, "제약·바이오"),
            target("KOSDAQ", "247540", "에코프로비엠", SymbolSector.SECONDARY_BATTERY, "2차전지"),
            target("KOSDAQ", "263750", "펄어비스", SymbolSector.ETC, "게임"),
            target("KOSDAQ", "293490", "카카오게임즈", SymbolSector.ETC, "게임"),
            target("KOSDAQ", "403870", "HPSP", SymbolSector.SEMICONDUCTOR, "반도체")
    );

    private static TargetSymbol target(String market, String ticker, String name,
                                       SymbolSector trainingSector, String exchangeSector) {
        return new TargetSymbol(market, ticker, name, trainingSector, exchangeSector);
    }

    record TargetSymbol(
            String market,
            String ticker,
            String name,
            SymbolSector trainingSector,
            String exchangeSector
    ) {
        String key() {
            return market + ":" + ticker;
        }
    }
}
