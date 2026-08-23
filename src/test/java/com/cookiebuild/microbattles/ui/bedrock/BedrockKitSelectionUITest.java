package com.cookiebuild.microbattles.ui.bedrock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.BedrockMenuSessionRegistry;

class BedrockKitSelectionUITest {
    @Test
    void everyCatalogKitFamilyGetsAResourcePackImage() {
        for (String kit : List.of("Default", "Explosive Archer", "Enderman", "Knockback Warrior", "Tank",
                "Ninja", "Archer", "Berserker", "Chemist", "Assassin", "Miner", "Vampire",
                "Frost Mage", "Juggernaut", "Trapper", "Alchemist", "Mobility")) {
            assertTrue(BedrockFormImages.isKnown(BedrockKitSelectionUI.imageId(kit)));
        }
        assertTrue(BedrockFormImages.isKnown(BedrockKitSelectionUI.imageId("Unknown future kit")));
    }

    @Test
    void allFourPagesShareTheSingleUseNonceRegistry() throws Exception {
        assertTrue(BedrockKitSelectionUI.class.getDeclaredField("sessions").getType()
                == BedrockMenuSessionRegistry.class);
    }

    @Test
    void snapshotRenderingRequiresMainThreadAndOnlinePlayer() {
        assertTrue(BedrockKitSelectionUI.renderEligible(true, true));
        assertFalse(BedrockKitSelectionUI.renderEligible(false, true));
        assertFalse(BedrockKitSelectionUI.renderEligible(true, false));
    }
}
