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

import java.nio.ByteBuffer;
import java.util.Base64;
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
    private static final byte CASE_KEY_VERSION = 1;
    private static final int CASE_KEY_BYTES = 25;

    private final MarketCandleRepository marketCandles;
    private final SymbolRepository symbols;

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
        String caseKey = encodeCaseKey(new CaseDescriptor(symbolId, startCandleId, analysisBars, futureBars));
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
        CaseDescriptor descriptor = decodeCaseKey(caseKey);
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

    private String encodeCaseKey(CaseDescriptor descriptor) {
        ByteBuffer buffer = ByteBuffer.allocate(CASE_KEY_BYTES);
        buffer.put(CASE_KEY_VERSION);
        buffer.putLong(descriptor.symbolId());
        buffer.putLong(descriptor.startCandleId());
        buffer.putInt(descriptor.analysisBars());
        buffer.putInt(descriptor.futureBars());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private CaseDescriptor decodeCaseKey(String caseKey) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(caseKey);
            if (bytes.length != CASE_KEY_BYTES) {
                throw new IllegalArgumentException("Unexpected case key length");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            if (buffer.get() != CASE_KEY_VERSION) {
                throw new IllegalArgumentException("Unsupported case key version");
            }
            long symbolId = buffer.getLong();
            long startCandleId = buffer.getLong();
            int analysisBars = buffer.getInt();
            int futureBars = buffer.getInt();
            if (symbolId <= 0 || startCandleId <= 0 || analysisBars < 20 || analysisBars > 240 || futureBars < 1 || futureBars > 120) {
                throw new IllegalArgumentException("Invalid case key values");
            }
            return new CaseDescriptor(symbolId, startCandleId, analysisBars, futureBars);
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.CONTENT_STUDIO_CASE_INVALID);
        }
    }

    private record CaseDescriptor(long symbolId, long startCandleId, int analysisBars, int futureBars) {
    }

    private record LoadedCase(CaseDescriptor descriptor, Symbol symbol, List<MarketCandle> window) {
    }
}
