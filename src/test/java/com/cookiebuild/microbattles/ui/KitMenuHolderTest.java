package com.cookiebuild.microbattles.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class KitMenuHolderTest {
    @Test
    void holderBindsMenuToOnePlayerAndPageWithoutUsingItsTitle() {
        UUID player = UUID.randomUUID();
        KitMenuHolder holder = new KitMenuHolder(player, 2);

        assertEquals(player, holder.playerId());
        assertEquals(2, holder.page());
        assertNull(holder.getInventory());
    }
}
