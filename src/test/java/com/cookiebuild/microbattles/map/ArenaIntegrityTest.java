package com.cookiebuild.microbattles.map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class ArenaIntegrityTest {
    @Test
    void acceptsFourGroundedArenaSpawnsWithNearbyTerrain() {
        assertDoesNotThrow(() -> ArenaIntegrity.validate("game-1", List.of(
                sample(32), sample(24), sample(48), sample(16))));
    }

    @Test
    void rejectsAnUngroundedSpawn() {
        assertThrows(IllegalStateException.class, () -> ArenaIntegrity.validate("void", List.of(
                sample(32), new ArenaIntegrity.SpawnSample(false, 40), sample(32), sample(32))));
    }

    @Test
    void rejectsAVisuallyEmptySpawnArea() {
        assertThrows(IllegalStateException.class, () -> ArenaIntegrity.validate("empty", List.of(
                sample(32), sample(32), sample(15), sample(32))));
    }

    private ArenaIntegrity.SpawnSample sample(int nearbySolidBlocks) {
        return new ArenaIntegrity.SpawnSample(true, nearbySolidBlocks);
    }
}
