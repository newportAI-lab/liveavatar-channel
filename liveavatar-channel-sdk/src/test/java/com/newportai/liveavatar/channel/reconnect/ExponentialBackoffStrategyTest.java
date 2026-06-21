package com.newportai.liveavatar.channel.reconnect;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExponentialBackoffStrategyTest {

    @Test
    public void testDefaultStrategyStopsAfterTenAttempts() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy();

        assertTrue(strategy.shouldContinue(10));
        assertFalse(strategy.shouldContinue(11));
    }
}
