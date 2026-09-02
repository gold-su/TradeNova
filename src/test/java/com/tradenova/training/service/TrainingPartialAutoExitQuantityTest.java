package com.tradenova.training.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingPartialAutoExitQuantityTest {

    @Test
    void calculatesFloorWithOneShareMinimumAndNeverExceedsPosition() {
        assertThat(TrainingTradeService.calculateExitQuantity(new BigDecimal("10"), 50))
                .isEqualByComparingTo("5");
        assertThat(TrainingTradeService.calculateExitQuantity(new BigDecimal("100"), 25))
                .isEqualByComparingTo("25");
        assertThat(TrainingTradeService.calculateExitQuantity(new BigDecimal("3"), 50))
                .isEqualByComparingTo("1");
        assertThat(TrainingTradeService.calculateExitQuantity(BigDecimal.ONE, 25))
                .isEqualByComparingTo("1");
        assertThat(TrainingTradeService.calculateExitQuantity(new BigDecimal("7"), 100))
                .isEqualByComparingTo("7");
    }
}
