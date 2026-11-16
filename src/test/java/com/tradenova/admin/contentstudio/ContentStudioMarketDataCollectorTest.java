package com.tradenova.admin.contentstudio;

import com.tradenova.common.exception.CustomException;
import com.tradenova.common.exception.ErrorCode;
import com.tradenova.kis.service.KisMarketDataService;
import com.tradenova.market.repository.MarketCandleRepository;
import com.tradenova.symbol.repository.SymbolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ContentStudioMarketDataCollectorTest {

    @Test
    void executionIsDisabledByDefault() {
        SymbolRepository symbols = mock(SymbolRepository.class);
        MarketCandleRepository candles = mock(MarketCandleRepository.class);
        KisMarketDataService kis = mock(KisMarketDataService.class);
        ContentStudioMarketDataCollector collector = new ContentStudioMarketDataCollector(
                symbols, candles, kis, mock(TransactionTemplate.class),
                new ContentStudioCollectionCursorCodec(), false, 0, 0);

        assertThatThrownBy(() -> collector.execute(new ContentStudioCollectionExecuteRequest(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null, 1)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_STUDIO_COLLECTION_DISABLED);

        verifyNoInteractions(symbols, candles, kis);
    }
}
