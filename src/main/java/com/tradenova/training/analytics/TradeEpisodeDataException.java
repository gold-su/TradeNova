package com.tradenova.training.analytics;

/**
 * Signals trade history that cannot be reconstructed without silently changing it.
 */
public class TradeEpisodeDataException extends IllegalArgumentException {

    public TradeEpisodeDataException(String message) {
        super(message);
    }
}
