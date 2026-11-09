package com.tradenova.admin.contentstudio;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.market.entity.MarketCandle;
import com.tradenova.market.repository.MarketCandleRepository;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentStudioService {

    static final int DEFAULT_ANALYSIS_BARS = 80;
    static final int DEFAULT_FUTURE_BARS = 40;
    private final MarketCandleRepository marketCandles;
    private final SymbolRepository symbols;
    private final ContentStudioCaseKeyCodec caseKeyCodec;

    public BlindContentCaseResponse randomCase(ContentStudioRandomRequest request) {
        int analysisBars = request != null && request.analysisBars() != null
                ? request.analysisBars() : DEFAULT_ANALYSIS_BARS;
        int futureBars = request != null && request.futureBars() != null
                ? request.futureBars() : DEFAULT_FUTURE_BARS;
        long seed = request != null && request.seed() != null
                ? request.seed() : ThreadLocalRandom.current().nextLong();

        int requiredBars = analysisBars + futureBars;
        List<Long> eligibleSymbolIds = marketCandles.findEligibleActiveSymbolIds(requiredBars);
        if (eligibleSymbolIds.isEmpty()) {
            throw new CustomException(ErrorCode.CONTENT_STUDIO_DATA_INSUFFICIENT);
        }

        SplittableRandom random = new SplittableRandom(seed);
        Long symbolId = eligibleSymbolIds.get(random.nextInt(eligibleSymbolIds.size()));
        List<MarketCandle> storedCandles = marketCandles.findAllBySymbol_IdOrderByCandleDateAsc(symbolId);
        int maxStartIndex = storedCandles.size() - requiredBars;
        if (maxStartIndex < 0) {
            throw new CustomException(ErrorCode.CONTENT_STUDIO_DATA_INSUFFICIENT);
        }

        int startIndex = random.nextInt(maxStartIndex + 1);
        long startCandleId = storedCandles.get(startIndex).getId();
        String caseKey = caseKeyCodec.encode(new ContentStudioCaseKeyCodec.CaseDescriptor(
                symbolId, startCandleId, analysisBars, futureBars));
        return loadBlindCase(caseKey, null);
    }

    public BlindContentCaseResponse loadBlindCase(String caseKey, Integer throughIndex) {
        LoadedCase loaded = loadCase(caseKey);
        int initialThroughIndex = loaded.descriptor().analysisBars() - 1;
        int resolvedThroughIndex = throughIndex == null ? initialThroughIndex : throughIndex;
        int lastIndex = loaded.window().size() - 1;
        if (resolvedThroughIndex < initialThroughIndex || resolvedThroughIndex > lastIndex) {
            throw new CustomException(ErrorCode.CONTENT_STUDIO_CASE_INVALID);
        }

        List<BlindCandleResponse> visible = IntStream.rangeClosed(0, resolvedThroughIndex)
                .mapToObj(index -> toBlindCandle(index, loaded.window().get(index)))
                .toList();

        return new BlindContentCaseResponse(
                caseKey,
                loaded.descriptor().analysisBars(),
                loaded.descriptor().futureBars(),
                resolvedThroughIndex,
                lastIndex - resolvedThroughIndex,
                resolvedThroughIndex == lastIndex,
                visible
        );
    }

    public ContentCaseRevealResponse reveal(String caseKey) {
        LoadedCase loaded = loadCase(caseKey);
        Symbol symbol = loaded.symbol();
        List<MarketCandle> window = loaded.window();
        int blindThroughIndex = loaded.descriptor().analysisBars() - 1;

        List<RevealedCandleResponse> candles = IntStream.range(0, window.size())
                .mapToObj(index -> toRevealedCandle(index, window.get(index)))
                .toList();

        return new ContentCaseRevealResponse(
                caseKey,
                symbol.getId(),
                symbol.getMarket(),
                symbol.getTicker(),
                symbol.getName(),
                symbol.getCurrency(),
                window.get(0).getCandleDate(),
                window.get(blindThroughIndex).getCandleDate(),
                window.get(window.size() - 1).getCandleDate(),
                loaded.descriptor().analysisBars(),
                loaded.descriptor().futureBars(),
                candles
        );
    }

    private LoadedCase loadCase(String caseKey) {
        ContentStudioCaseKeyCodec.CaseDescriptor descriptor = caseKeyCodec.decode(caseKey);
        Symbol symbol = symbols.findById(descriptor.symbolId())
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_STUDIO_CASE_INVALID));
        List<MarketCandle> allCandles = marketCandles.findAllBySymbol_IdOrderByCandleDateAsc(symbol.getId());
        int startIndex = -1;
        for (int index = 0; index < allCandles.size(); index++) {
            if (descriptor.startCandleId() == allCandles.get(index).getId()) {
                startIndex = index;
                break;
            }
        }
        int totalBars = descriptor.analysisBars() + descriptor.futureBars();
        if (startIndex < 0 || startIndex + totalBars > allCandles.size()) {
            throw new CustomException(ErrorCode.CONTENT_STUDIO_CASE_INVALID);
        }
        return new LoadedCase(descriptor, symbol, List.copyOf(allCandles.subList(startIndex, startIndex + totalBars)));
    }

    private BlindCandleResponse toBlindCandle(int index, MarketCandle candle) {
        return new BlindCandleResponse(index, candle.getOpenPrice(), candle.getHighPrice(),
                candle.getLowPrice(), candle.getClosePrice(), candle.getVolume());
    }

    private RevealedCandleResponse toRevealedCandle(int index, MarketCandle candle) {
        return new RevealedCandleResponse(index, candle.getCandleDate(), candle.getOpenPrice(), candle.getHighPrice(),
                candle.getLowPrice(), candle.getClosePrice(), candle.getVolume());
    }

    private record LoadedCase(ContentStudioCaseKeyCodec.CaseDescriptor descriptor,
                              Symbol symbol,
                              List<MarketCandle> window) {
    }
}
