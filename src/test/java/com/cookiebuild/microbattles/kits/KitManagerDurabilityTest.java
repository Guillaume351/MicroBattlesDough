package com.cookiebuild.microbattles.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void reconnectSelectionOnlyAcceptsCataloguedKitLevels() {
        assertTrue(KitManager.isRestorableSelection("Default:0"));
        assertTrue(KitManager.isRestorableSelection("Tank:1"));
        assertFalse(KitManager.isRestorableSelection("Tank:99"));
        assertFalse(KitManager.isRestorableSelection("Unknown:1"));
        assertFalse(KitManager.isRestorableSelection("Tank"));
    }
}
