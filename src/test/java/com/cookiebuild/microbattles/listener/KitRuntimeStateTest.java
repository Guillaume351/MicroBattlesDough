package com.cookiebuild.microbattles.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class KitRuntimeStateTest {
    @Test
    void cooldownKeepsElapsedWallClockTimeAcrossReconnectWindow() {
        KitRuntimeState state = new KitRuntimeState();
        UUID playerId = UUID.randomUUID();

        assertEquals(0L, state.useCooldown(playerId, "ice_spell", 12_000L, 1_000L));
        assertEquals(11L, state.useCooldown(playerId, "ice_spell", 12_000L, 2_000L));
        assertEquals(0L, state.useCooldown(playerId, "ice_spell", 12_000L, 13_000L));
    }

    @Test
    void assassinBonusSurvivesBriefDisconnectButNeverOutlivesItsExpiry() {
        KitRuntimeState state = new KitRuntimeState();
        UUID playerId = UUID.randomUUID();

        state.armAssassinBonus(playerId, 4_000L);
        assertTrue(state.consumeAssassinBonus(playerId, 3_000L));
        state.armAssassinBonus(playerId, 4_000L);
        assertFalse(state.consumeAssassinBonus(playerId, 4_001L));
    }

    @Test
    void finalRemovalClearsEveryAbilityTimer() {
        KitRuntimeState state = new KitRuntimeState();
        UUID playerId = UUID.randomUUID();
        state.useCooldown(playerId, "brew_potion", 10_000L, 1_000L);
        state.armAssassinBonus(playerId, 9_000L);
        state.clear(playerId);

        assertEquals(0L, state.useCooldown(playerId, "brew_potion", 10_000L, 2_000L));
        assertFalse(state.consumeAssassinBonus(playerId, 2_000L));
    }
}
