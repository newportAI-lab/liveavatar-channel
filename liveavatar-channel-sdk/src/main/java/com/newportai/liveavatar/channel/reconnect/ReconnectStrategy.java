package com.newportai.liveavatar.channel.reconnect;

/**
 * Reconnect strategy interface
 */
public interface ReconnectStrategy {

    /**
     * Calculate reconnect delay for given attempt
     *
     * @param attempt reconnect attempt number (1-based)
     * @return delay in milliseconds
     */
    long getDelayMillis(int attempt);

    /**
     * Check if should continue reconnecting
     *
     * @param attempt reconnect attempt number (1-based)
     * @return true if should continue, false otherwise
     */
    default boolean shouldContinue(int attempt) {
        return true; // By default, reconnect indefinitely
    }
}
