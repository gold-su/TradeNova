package com.tradenova.kis.util;

public final class KisMarketCodeMapper {

    private KisMarketCodeMapper(){}

    /**
     * Symbol.market -> KIS marketCode 매핑
     * KIS: J(KRX), NX(NXT), UN(통합)
     */
    public static String toMarketCode(String market){
        if(market == null) return "J";

        return switch (market.toUpperCase()){
            case "KOSPI", "KOSDAQ", "KRX" -> "J";
            case "NXT", "NX" -> "NX";
            case "UN", "UNION" -> "UN";
            default -> "J";
        };
    }

}
