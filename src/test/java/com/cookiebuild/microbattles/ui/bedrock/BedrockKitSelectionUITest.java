package com.cookiebuild.microbattles.ui.bedrock;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BedrockKitSelectionUITest {
    @Test
    void everyCatalogKitFamilyGetsAResourcePackImage() {
        for (String kit : List.of("Default", "Explosive Archer", "Enderman", "Knockback Warrior", "Tank",
                "Ninja", "Archer", "Berserker", "Chemist", "Assassin", "Miner", "Vampire",
                "Frost Mage", "Juggernaut", "Trapper", "Alchemist", "Mobility")) {
            assertTrue(BedrockKitSelectionUI.imageId(kit).startsWith("kits/microbattles/"));
        }
    }
}
