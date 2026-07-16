package com.cookiebuild.microbattles.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class KitSelectorOwnershipTest {
    @Test
    void rejectsSkyWarsCookieWithTheSameVisibleName() {
        assertFalse(KitSelectorOwnership.isMicroBattlesSelector(Material.COOKIE, false));
    }

    @Test
    void acceptsMarkedMicroBattlesCookie() {
        assertTrue(KitSelectorOwnership.isMicroBattlesSelector(Material.COOKIE, true));
    }

    @Test
    void rejectsMarkedNonCookieItem() {
        assertFalse(KitSelectorOwnership.isMicroBattlesSelector(Material.CHEST, true));
    }
}
