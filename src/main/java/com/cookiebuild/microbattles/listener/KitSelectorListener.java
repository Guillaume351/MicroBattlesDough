package com.cookiebuild.microbattles.listener;

import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.geyser.api.GeyserApi;

import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.microbattles.ui.ImprovedKitSelectionUI;

public class KitSelectorListener implements Listener {

    private final ImprovedKitSelectionUI kitSelectionUI;
    private final MinigameStatsService statsService;

    public static final String KIT_SELECTOR_NAME = ChatColor.GOLD + "Kit Selector";

    public KitSelectorListener(ImprovedKitSelectionUI kitSelectionUI, MinigameStatsService statsService) {
        this.kitSelectionUI = kitSelectionUI;
        this.statsService = statsService;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.COOKIE) {
            return;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }

        String displayName = item.getItemMeta().getDisplayName();
        if (!displayName.equals(KIT_SELECTOR_NAME)) {
            return;
        }

        event.setCancelled(true);

        // Ouvrir l'interface de sélection des kits
        UUID playerId = player.getUniqueId();
        if (GeyserApi.api().isBedrockPlayer(playerId)) {
            kitSelectionUI.openKitSelectionForm(player);
        } else {
            kitSelectionUI.openKitSelectionGUI(player);
        }
    }

    /**
     * Crée un cookie de sélection de kit
     */
    public static ItemStack createKitSelectorCookie() {
        ItemStack cookie = new ItemStack(Material.COOKIE);
        ItemMeta meta = cookie.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(KIT_SELECTOR_NAME);
            meta.setLore(java.util.Arrays.asList(
                    ChatColor.GRAY + "Clic droit pour choisir votre kit !",
                    ChatColor.YELLOW + "Utilisez-moi avant le début de la partie"));
            cookie.setItemMeta(meta);
        }

        return cookie;
    }
}