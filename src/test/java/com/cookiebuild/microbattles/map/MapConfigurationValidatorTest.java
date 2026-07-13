package com.cookiebuild.microbattles.map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
