package com.cookiebuild.microbattles.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.junit.jupiter.api.Test;

class KitSelectionUIContractTest {
    @Test
    void topInventoryDragProtectionIsRegistered() throws Exception {
        var method = KitSelectionUI.class.getDeclaredMethod("onInventoryDrag", InventoryDragEvent.class);
        assertTrue(method.isAnnotationPresent(EventHandler.class));
        assertTrue(KitSelectionUI.dragTouchesTop(java.util.Set.of(4, 55), 54));
        assertFalse(KitSelectionUI.dragTouchesTop(java.util.Set.of(54, 60), 54));
    }
}
