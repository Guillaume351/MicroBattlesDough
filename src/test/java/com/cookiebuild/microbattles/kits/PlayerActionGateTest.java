package com.cookiebuild.microbattles.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class PlayerActionGateTest {
    @Test
    void twentyRapidClicksCollapseToOneMutationThenRespectCooldown() {
        AtomicLong now = new AtomicLong(1_000L);
        PlayerActionGate gate = new PlayerActionGate(750L, now::get);
        UUID playerId = UUID.randomUUID();
        AtomicInteger admitted = new AtomicInteger();

        IntStream.range(0, 20).parallel().forEach(ignored -> {
            if (gate.tryBegin(playerId)) admitted.incrementAndGet();
        });
        assertEquals(1, admitted.get());

        gate.finish(playerId);
        assertFalse(gate.tryBegin(playerId));
        now.addAndGet(750L);
        assertTrue(gate.tryBegin(playerId));
    }
}
