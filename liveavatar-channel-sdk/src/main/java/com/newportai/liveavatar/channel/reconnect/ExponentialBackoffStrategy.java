package com.newportai.liveavatar.channel.reconnect;

/**
 * Exponential backoff reconnect strategy
 *
 * <p>Increases delay exponentially: 1s, 2s, 4s, 8s, 16s, 32s, 60s (max)
 */
public class ExponentialBackoffStrategy implements ReconnectStrategy {

    private static final long INITIAL_DELAY_MS = 1000;      // 1 second
    private static final long MAX_DELAY_MS = 60000;         // 60 seconds
    private static final double MULTIPLIER = 2.0;

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;

    /**
     * Create strategy with default parameters
     */
    public ExponentialBackoffStrategy() {
        this(INITIAL_DELAY_MS, MAX_DELAY_MS, MULTIPLIER);
    }

    /**
     * Create strategy with custom parameters
     *
     * @param initialDelayMs initial delay in milliseconds
     * @param maxDelayMs maximum delay in milliseconds
     * @param multiplier delay multiplier (typically 2.0)
     */
    public ExponentialBackoffStrategy(long initialDelayMs, long maxDelayMs, double multiplier) {
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
    }

    @Override
    public long getDelayMillis(int attempt) {
        if (attempt <= 0) {
            return initialDelayMs;
        }

        // Calculate exponential delay: initialDelay * multiplier^(attempt-1)
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt - 1));

        // Cap at maximum delay
        return Math.min(delay, maxDelayMs);
    }

    @Override
    public String toString() {
        return "ExponentialBackoffStrategy{" +
                "initialDelayMs=" + initialDelayMs +
                ", maxDelayMs=" + maxDelayMs +
                ", multiplier=" + multiplier +
                '}';
    }
}
