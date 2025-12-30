package com.tradenova.training;

import com.tradenova.kis.KisMarketDataService;
import com.tradenova.kis.dto.CandleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/training")
public class TrainingCandlesController {

    private final KisMarketDataService KisMarketDataService;

    /**
     * 테스트용 : 캔들 조회
     *
     * 예:
     * /api/training/candles?symbol=005930&from=20240101&to=20240601&period=D
     */
    @GetMapping("/candles")
    public List<CandleDto> candles(
            @RequestParam String symbol,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "D") String period
    )
    {
        // TODO: MVP 기본값 예시
        String marketCode = "J";     // KRX
        String adjPrice = "0";       // 문서값 기준. (실제로 수정주가 반영이 어느 값인지 테스트 후 고정)
        return KisMarketDataService.getCandles(marketCode, symbol, from, to, period, adjPrice);
    }

}
