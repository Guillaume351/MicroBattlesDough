package com.cookiebuild.microbattles.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class KitManagerDurabilityTest {
    @Test
    void failedDurableSelectionNeverUpdatesGameplayCache() {
        AtomicInteger cacheWrites = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> KitManager.persistThenCache(
                () -> { throw new IllegalStateException("database unavailable"); },
                cacheWrites::incrementAndGet));

        assertEquals(0, cacheWrites.get());
    }
}
