package com.sft.data;

/**
 * The one exception type crossing the data -> UI boundary for holdings.
 * Thrown when finance.db is missing or the query fails, so callers (the
 * HOLDINGS page) never have to deal with raw SQLException.
 */
public class HoldingsUnavailableException extends RuntimeException {

    public HoldingsUnavailableException(String message) {
        super(message);
    }

    public HoldingsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
