package com.cookiebuild.microbattles.map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MapConfigurationValidatorTest {
    private static final List<List<? extends Number>> SPAWNS = List.of(
            List.of(1, 2, 3), List.of(4.5, 5, 6.5), List.of(7, 8, 9), List.of(10, 11, 12));

    @Test
    void acceptsFourNumericSpawnsAndWallSeeds() {
        assertDoesNotThrow(() -> MapConfigurationValidator.validate("arena", SPAWNS,
                List.of(1, 18, 10, 20, 30, 40)));
    }

    @Test
    void rejectsMissingTeamAndMalformedWallCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> MapConfigurationValidator.validate("arena", SPAWNS.subList(0, 3), List.of(1, 18, 10, 20)));
        assertThrows(IllegalArgumentException.class,
                () -> MapConfigurationValidator.validate("arena", SPAWNS, List.of(1, 18, 10)));
    }

    @Test
    void voidLevelDefaultsToZeroAndMustStayBelowEverySpawn() {
        assertEquals(0, MapConfigurationValidator.resolveVoidY("arena", null, SPAWNS));
        assertEquals(-5, MapConfigurationValidator.resolveVoidY("arena", -5, SPAWNS));
        assertEquals(1, MapConfigurationValidator.resolveVoidY("arena", 1.0, SPAWNS));
        assertThrows(IllegalArgumentException.class,
                () -> MapConfigurationValidator.resolveVoidY("arena", 2, SPAWNS));
        assertThrows(IllegalArgumentException.class,
                () -> MapConfigurationValidator.resolveVoidY("arena", 0.5, SPAWNS));
        assertThrows(IllegalArgumentException.class,
                () -> MapConfigurationValidator.resolveVoidY("arena", "low", SPAWNS));
    }

    @Test
    void mapUsesItsConfiguredVoidLevel() {
        GameMap map = new GameMap("arena");
        assertTrue(map.isBelowVoid(-0.1));
        assertFalse(map.isBelowVoid(0));
        map.setVoidY(10);
        assertTrue(map.isBelowVoid(9.9));
        assertFalse(map.isBelowVoid(10));
    }
}
