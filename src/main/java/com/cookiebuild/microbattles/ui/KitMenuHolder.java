package com.cookiebuild.microbattles.ui;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Collision-safe ownership marker for the Java kit selector. */
final class KitMenuHolder implements InventoryHolder {
    private final UUID playerId;
    private final int page;
    private Inventory inventory;

    KitMenuHolder(UUID playerId, int page) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.page = page;
    }

    void bind(Inventory inventory) { this.inventory = Objects.requireNonNull(inventory, "inventory"); }
    UUID playerId() { return playerId; }
    int page() { return page; }
    @Override public Inventory getInventory() { return inventory; }
}
