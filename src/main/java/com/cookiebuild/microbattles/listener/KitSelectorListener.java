package com.cookiebuild.microbattles.listener;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.ui.KitSelectionUI;
import com.cookiebuild.cookiedough.ui.MenuLore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class KitSelectorListener implements Listener {

    private static final String KIT_SELECTOR_NAME = "§6Kit Selector";
    private final KitSelectionUI kitSelectionUI;

    public KitSelectorListener(KitManager kitManager, MinigameProgressionService minigameStatsService,
            com.cookiebuild.cookiedough.service.PlayerStatsService playerStatsService) {
        this.kitSelectionUI = new KitSelectionUI(kitManager, minigameStatsService, playerStatsService);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        boolean hasMarker = meta != null && meta.getPersistentDataContainer().has(
                MicroBattles.getInstance().getKitSelectorKey(), PersistentDataType.BYTE);
        if (!KitSelectorOwnership.isMicroBattlesSelector(item.getType(), hasMarker)) {
            return;
        }

        event.setCancelled(true);
        kitSelectionUI.openKitSelectionGUI(player);
    }

    public static ItemStack createKitSelectorCookie() {
        ItemStack kitSelector = new ItemStack(Material.COOKIE);
        ItemMeta meta = kitSelector.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(KIT_SELECTOR_NAME);
            meta.setLore(List.of(
                    MenuLore.legacyDetail("Right-click to select your kit"),
                    MenuLore.legacyDetail("Choose from available kits"),
                    MenuLore.legacyDetail("and their different tiers")));
            meta.getPersistentDataContainer().set(
                    MicroBattles.getInstance().getKitSelectorKey(), PersistentDataType.BYTE, (byte) 1);
            kitSelector.setItemMeta(meta);
        }
        return kitSelector;
    }

    public static void giveKitSelectorCookie(Player player) {
        player.getInventory().setItem(0, createKitSelectorCookie());
        player.sendMessage(Component.text("Use the cookie to select your kit!").color(NamedTextColor.YELLOW));
    }

    public KitSelectionUI getKitSelectionUI() {
        return kitSelectionUI;
    }
}
