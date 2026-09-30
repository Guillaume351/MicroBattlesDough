package com.cookiebuild.microbattles.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

class MapDisplayNamesTest {
    private static final IntFunction<String> CARTE = number -> "Carte " + number;

    @Test
    void genericNumberedMapsUseTheLocalizedLabel() {
        assertEquals("Carte 3", MapDisplayNames.displayName("game-3", null, CARTE));
        assertEquals("Carte 12", MapDisplayNames.displayName("GAME_12", "", CARTE));
        assertEquals("Carte 4", MapDisplayNames.displayName("arena4", "  ", CARTE));
    }

    @Test
    void configuredDisplayNameWins() {
        assertEquals("Île Céleste", MapDisplayNames.displayName("game-5", " Île Céleste ", CARTE));
    }

    @Test
    void otherIdentifiersAreTitleCased() {
        assertEquals("Frozen Peaks", MapDisplayNames.displayName("frozen_peaks", null, CARTE));
        assertEquals("Desert Ruins 2", MapDisplayNames.displayName("desert-ruins-2", null, CARTE));
        assertEquals("", MapDisplayNames.displayName(null, null, CARTE));
    }
}
