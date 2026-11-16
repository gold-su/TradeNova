package com.tradenova.admin.contentstudio;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.kis.dto.CandleDto;
import com.tradenova.kis.service.KisMarketDataService;
import com.tradenova.market.entity.MarketCandle;
import com.tradenova.market.repository.MarketCandleCoverage;
import com.tradenova.market.repository.MarketCandleRepository;
import com.tradenova.symbol.entity.Symbol;
import com.tradenova.symbol.repository.SymbolRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContentStudioMarketDataCollector {

    private static final int KIS_PAGE_SIZE = 100;
    private static final int DEFAULT_MAX_REQUESTS = 10;
    private static final int MAX_RETRIES = 3;
    private static final long MAX_RANGE_DAYS = 3_653;
    private static final DateTimeFormatter KIS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SymbolRepository symbols;
    private final MarketCandleRepository candles;
    private final KisMarketDataService kisMarketDataService;
    private final TransactionTemplate transactionTemplate;
    private final ContentStudioCollectionCursorCodec cursorCodec;
    private final boolean executionEnabled;
    private final long requestDelayMillis;
    private final long retryBaseDelayMillis;

    public ContentStudioMarketDataCollector(
            SymbolRepository symbols,
            MarketCandleRepository candles,
            KisMarketDataService kisMarketDataService,
            TransactionTemplate transactionTemplate,
            ContentStudioCollectionCursorCodec cursorCodec,
            @Value("${app.content-studio.bulk-collector.enabled:false}") boolean executionEnabled,
            @Value("${app.content-studio.bulk-collector.request-delay-ms:100}") long requestDelayMillis,
            @Value("${app.content-studio.bulk-collector.retry-base-delay-ms:250}") long retryBaseDelayMillis
    ) {
        this.symbols = symbols;
        this.candles = candles;
        this.kisMarketDataService = kisMarketDataService;
        this.transactionTemplate = transactionTemplate;
        this.cursorCodec = cursorCodec;
        this.executionEnabled = executionEnabled;
        this.requestDelayMillis = Math.max(0, requestDelayMillis);
        this.retryBaseDelayMillis = Math.max(0, retryBaseDelayMillis);
    }

    @Transactional(readOnly = true)
    public ContentStudioCollectionPlanResponse dryRun(ContentStudioCollectionRequest request) {
        DateRange range = validateRange(request.fromDate(), request.toDate());
        List<ContentStudioMarketDataCatalog.TargetSymbol> targets = ContentStudioMarketDataCatalog.TARGETS;
        List<String> tickers = targets.stream().map(ContentStudioMarketDataCatalog.TargetSymbol::ticker).distinct().toList();
        Map<String, Symbol> existingByKey = new HashMap<>();
        for (Symbol symbol : symbols.findAllByTickerIn(tickers)) {
            existingByKey.put(key(symbol.getMarket(), symbol.getTicker()), symbol);
        }

        List<Long> symbolIds = existingByKey.values().stream().map(Symbol::getId).toList();
        Map<Long, MarketCandleCoverage> coverageBySymbol = new HashMap<>();
        if (!symbolIds.isEmpty()) {
            for (MarketCandleCoverage coverage : candles.findCoverageBySymbolIdsAndDateRange(
                    symbolIds, range.fromDate(), range.toDate())) {
                coverageBySymbol.put(coverage.symbolId(), coverage);
            }
        }

        long weekdayUpperBound = countWeekdays(range.fromDate(), range.toDate());
        long requestsPerSymbol = divideRoundUp(weekdayUpperBound, KIS_PAGE_SIZE);
        long totalNewUpperBound = 0;
        int existingSymbolCount = 0;
        List<ContentStudioCollectionPlanResponse.SymbolPlan> plans = new ArrayList<>();

        for (ContentStudioMarketDataCatalog.TargetSymbol target : targets) {
            Symbol symbol = existingByKey.get(target.key());
            MarketCandleCoverage coverage = symbol == null ? null : coverageBySymbol.get(symbol.getId());
            long existingCount = coverage == null ? 0 : coverage.candleCount();
            long newUpperBound = Math.max(0, weekdayUpperBound - existingCount);
            totalNewUpperBound += newUpperBound;
            if (symbol != null) {
                existingSymbolCount++;
            }
            plans.add(new ContentStudioCollectionPlanResponse.SymbolPlan(
                    target.market(), target.ticker(), target.name(), symbol != null, existingCount,
                    coverage == null ? null : coverage.firstDate(),
                    coverage == null ? null : coverage.lastDate(),
                    requestsPerSymbol, newUpperBound
            ));
        }

        return new ContentStudioCollectionPlanResponse(
                range.fromDate(), range.toDate(), targets.size(), existingSymbolCount,
                targets.size() - existingSymbolCount, weekdayUpperBound,
                requestsPerSymbol * targets.size(), totalNewUpperBound, List.copyOf(plans)
        );
    }

    public ContentStudioCollectionExecutionResponse execute(ContentStudioCollectionExecuteRequest request) {
        if (!executionEnabled) {
            throw new CustomException(ErrorCode.CONTENT_STUDIO_COLLECTION_DISABLED);
        }
        DateRange range = validateRange(request.fromDate(), request.toDate());
        int maxRequests = request.maxRequests() == null ? DEFAULT_MAX_REQUESTS : request.maxRequests();
        if (maxRequests < 1 || maxRequests > 50) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        ContentStudioCollectionCursorCodec.Cursor cursor = initialOrDecodedCursor(request, range);
        int symbolIndex = cursor.symbolIndex();
        LocalDate nextEndDate = cursor.nextEndDate();
        int requestsMade = 0;
        long savedCandles = 0;
        long existingCandlesSkipped = 0;
        long invalidCandlesSkipped = 0;
        List<ContentStudioCollectionExecutionResponse.PageResult> pages = new ArrayList<>();

        while (symbolIndex < ContentStudioMarketDataCatalog.TARGETS.size() && requestsMade < maxRequests) {
            if (requestsMade > 0) {
                sleep(requestDelayMillis);
            }
            ContentStudioMarketDataCatalog.TargetSymbol target = ContentStudioMarketDataCatalog.TARGETS.get(symbolIndex);
            FetchResult fetch = fetchWithRetry(target, range.fromDate(), nextEndDate);
            requestsMade++;

            WriteResult written = writePage(target, fetch.candles(), range);
            savedCandles += written.savedCandles();
            existingCandlesSkipped += written.existingCandlesSkipped();
            invalidCandlesSkipped += written.invalidCandlesSkipped();
            pages.add(new ContentStudioCollectionExecutionResponse.PageResult(
                    target.market(), target.ticker(), nextEndDate, fetch.attempts(), fetch.candles().size(),
                    written.savedCandles(), written.existingCandlesSkipped(), written.invalidCandlesSkipped()
            ));

            if (fetch.candles().isEmpty()) {
                symbolIndex++;
                nextEndDate = range.toDate();
                continue;
            }

            LocalDate oldestDate = fetch.candles().stream()
                    .map(candle -> toSeoulDate(candle.t()))
                    .min(LocalDate::compareTo)
                    .orElseThrow(() -> new CustomException(ErrorCode.KIS_MARKET_DATA_INVALID));
            boolean symbolComplete = fetch.candles().size() < KIS_PAGE_SIZE
                    || !oldestDate.isAfter(range.fromDate());
            if (symbolComplete) {
                symbolIndex++;
                nextEndDate = range.toDate();
            } else {
                LocalDate candidate = oldestDate.minusDays(1);
                if (!candidate.isBefore(nextEndDate)) {
                    throw new CustomException(ErrorCode.KIS_MARKET_DATA_INVALID);
                }
                nextEndDate = candidate;
            }
        }

        boolean completed = symbolIndex >= ContentStudioMarketDataCatalog.TARGETS.size();
        String nextToken = completed ? null : cursorCodec.encode(new ContentStudioCollectionCursorCodec.Cursor(
                range.fromDate(), range.toDate(), symbolIndex, nextEndDate));
        String nextTicker = completed ? null : ContentStudioMarketDataCatalog.TARGETS.get(symbolIndex).ticker();

        return new ContentStudioCollectionExecutionResponse(
                completed, nextToken, requestsMade, savedCandles, existingCandlesSkipped,
                invalidCandlesSkipped, completed ? null : symbolIndex, nextTicker, List.copyOf(pages)
        );
    }

    private ContentStudioCollectionCursorCodec.Cursor initialOrDecodedCursor(
            ContentStudioCollectionExecuteRequest request, DateRange range) {
        if (request.resumeToken() == null || request.resumeToken().isBlank()) {
            return new ContentStudioCollectionCursorCodec.Cursor(range.fromDate(), range.toDate(), 0, range.toDate());
        }
        ContentStudioCollectionCursorCodec.Cursor cursor = cursorCodec.decode(request.resumeToken());
        if (!cursor.fromDate().equals(range.fromDate())
                || !cursor.toDate().equals(range.toDate())
                || cursor.symbolIndex() < 0
                || cursor.symbolIndex() >= ContentStudioMarketDataCatalog.TARGETS.size()
                || cursor.nextEndDate().isBefore(range.fromDate())
                || cursor.nextEndDate().isAfter(range.toDate())) {
            throw new CustomException(ErrorCode.CONTENT_STUDIO_COLLECTION_CURSOR_INVALID);
        }
        return cursor;
    }

    private FetchResult fetchWithRetry(ContentStudioMarketDataCatalog.TargetSymbol target,
                                       LocalDate fromDate, LocalDate toDate) {
        CustomException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                List<CandleDto> result = kisMarketDataService.getCandles(
                        "J", target.ticker(), fromDate.format(KIS_DATE), toDate.format(KIS_DATE), "D", "0");
                return new FetchResult(result == null ? List.of() : result, attempt);
            } catch (CustomException exception) {
                if (!isRetryable(exception) || attempt == MAX_RETRIES) {
                    throw exception;
                }
                lastFailure = exception;
                sleep(retryBaseDelayMillis * (1L << (attempt - 1)));
            }
        }
        throw lastFailure == null ? new CustomException(ErrorCode.KIS_API_CALL_FAILED) : lastFailure;
    }

    private boolean isRetryable(CustomException exception) {
        return exception.getErrorCode() == ErrorCode.KIS_API_CALL_FAILED
                || exception.getErrorCode() == ErrorCode.KIS_API_ERROR
                || exception.getErrorCode() == ErrorCode.KIS_RESPONSE_EMPTY
                || exception.getErrorCode() == ErrorCode.KIS_TOKEN_RESPONSE_EMPTY;
    }

    private WriteResult writePage(ContentStudioMarketDataCatalog.TargetSymbol target,
                                  List<CandleDto> page, DateRange range) {
        WriteResult result = transactionTemplate.execute(status -> {
            Map<LocalDate, CandleDto> validByDate = new LinkedHashMap<>();
            int invalid = 0;
            for (CandleDto candle : page) {
                LocalDate date = toSeoulDate(candle.t());
                if (date.isBefore(range.fromDate()) || date.isAfter(range.toDate()) || !isValid(candle)) {
                    invalid++;
                    continue;
                }
                validByDate.putIfAbsent(date, candle);
            }
            invalid += page.size() - invalid - validByDate.size();
            if (validByDate.isEmpty()) {
                return new WriteResult(0, 0, invalid);
            }

            Symbol symbol = symbols.findByMarketAndTicker(target.market(), target.ticker())
                    .orElseGet(() -> symbols.save(Symbol.builder()
                            .market(target.market())
                            .ticker(target.ticker())
                            .name(target.name())
                            .currency("KRW")
                            .active(true)
                            .trainingSector(target.trainingSector())
                            .exchangeSector(target.exchangeSector())
                            .build()));

            Set<LocalDate> existing = new HashSet<>(candles.findCandleDates(
                    symbol.getId(), range.fromDate(), range.toDate()));
            List<MarketCandle> additions = new ArrayList<>();
            for (Map.Entry<LocalDate, CandleDto> entry : validByDate.entrySet()) {
                if (existing.contains(entry.getKey())) {
                    continue;
                }
                CandleDto candle = entry.getValue();
                additions.add(MarketCandle.builder()
                        .symbol(symbol)
                        .candleDate(entry.getKey())
                        .openPrice(candle.o())
                        .highPrice(candle.h())
                        .lowPrice(candle.l())
                        .closePrice(candle.c())
                        .volume((long) candle.v())
                        .build());
            }
            if (!additions.isEmpty()) {
                candles.saveAll(additions);
            }
            return new WriteResult(additions.size(), validByDate.size() - additions.size(), invalid);
        });
        if (result == null) {
            throw new IllegalStateException("Market candle page transaction returned no result");
        }
        return result;
    }

    private DateRange validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)
                || ChronoUnit.DAYS.between(fromDate, toDate) > MAX_RANGE_DAYS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return new DateRange(fromDate, toDate);
    }

    private boolean isValid(CandleDto candle) {
        return isPositiveFinite(candle.o()) && isPositiveFinite(candle.h())
                && isPositiveFinite(candle.l()) && isPositiveFinite(candle.c())
                && Double.isFinite(candle.v()) && candle.v() >= 0 && candle.v() <= Long.MAX_VALUE
                && candle.l() <= Math.min(candle.o(), candle.c())
                && candle.h() >= Math.max(candle.o(), candle.c())
                && candle.l() <= candle.h();
    }

    private boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private LocalDate toSeoulDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(SEOUL).toLocalDate();
    }

    private long countWeekdays(LocalDate fromDate, LocalDate toDate) {
        long count = 0;
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    private long divideRoundUp(long value, long divisor) {
        return value == 0 ? 0 : (value + divisor - 1) / divisor;
    }

    private String key(String market, String ticker) {
        return market + ":" + ticker;
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.KIS_API_CALL_FAILED);
        }
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {
    }

    private record FetchResult(List<CandleDto> candles, int attempts) {
    }

    private record WriteResult(int savedCandles, int existingCandlesSkipped, int invalidCandlesSkipped) {
    }
}
