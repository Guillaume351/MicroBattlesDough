package com.cookiebuild.microbattles.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class WeeklyKitRotationTest {
    private static final List<String> KITS = List.of("Default", "Archer", "Miner", "Tank", "Ninja");

    @Test
    void rotationIsStableAndExcludesDefault() {
        LocalDate date = LocalDate.of(2026, 7, 13);
        List<String> first = WeeklyKitRotation.forDate(KITS, date);
        assertEquals(first, WeeklyKitRotation.forDate(KITS, date));
        assertEquals(2, first.size());
        assertFalse(first.contains("Default"));
        assertTrue(first.stream().allMatch(KITS::contains));
    }

    @Test
    void containsUsesTheSameRotation() {
        LocalDate date = LocalDate.of(2026, 7, 13);
        String freeKit = WeeklyKitRotation.forDate(KITS, date).getFirst();
        assertTrue(WeeklyKitRotation.contains(KITS, date, freeKit.toLowerCase()));
    }
}
