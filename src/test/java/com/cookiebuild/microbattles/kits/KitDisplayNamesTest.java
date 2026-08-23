package com.cookiebuild.microbattles.kits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class KitDisplayNamesTest {
    @Test
    void localizesPresentationWithoutChangingStableIdentifiers() {
        assertEquals("Archer", KitDisplayNames.localized("Archer", Locale.ENGLISH));
        assertEquals("Archer", KitDisplayNames.localized("Archer", Locale.FRENCH));
        assertEquals("Стрелец II", KitDisplayNames.localizedTier("Archer", 2, Locale.of("bg")));
        assertEquals("तीरंदाज़ III", KitDisplayNames.localizedTier("Archer", 3, Locale.of("hi")));
        assertEquals("Future Kit", KitDisplayNames.localized("Future Kit", Locale.of("bg")));
    }

    @Test
    void everyStableKitHasBulgarianAndHindiPresentation() {
        List<String> stableNames = List.of(
                "Default", "Explosive Archer", "Enderman", "Knockback Warrior", "Tank", "Ninja",
                "Archer", "Berserker", "Chemist", "Assassin", "Miner", "Vampire", "Frost Mage",
                "Juggernaut", "Trapper", "Alchemist", "Mobility");
        for (String stableName : stableNames) {
            assertFalse(KitDisplayNames.localized(stableName, Locale.of("bg")).isBlank());
            assertFalse(KitDisplayNames.localized(stableName, Locale.of("hi")).isBlank());
        }
    }
}
