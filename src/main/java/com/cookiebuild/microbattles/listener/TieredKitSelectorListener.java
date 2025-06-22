package com.cookiebuild.microbattles.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.kits.TieredKitManager;
import com.cookiebuild.microbattles.ui.TieredKitSelectionUI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TieredKitSelectorListener implements Listener {

    private static final String KIT_SELECTOR_NAME = "§6Kit Selector";
    private final TieredKitSelectionUI kitSelectionUI;

    public TieredKitSelectorListener() {
        TieredKitManager tieredKitManager = TieredKitManager.getInstance(KitManager.getInstance());
        MinigameStatsService statsService = CookieDough.createMinigameStatsService();
        this.kitSelectionUI = new TieredKitSelectionUI(tieredKitManager, statsService);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.COOKIE) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !KIT_SELECTOR_NAME.equals(meta.getDisplayName())) {
            return;
        }

        event.setCancelled(true);

        // Ouvrir l'interface de sélection de kit
        kitSelectionUI.openKitSelectionGUI(player);
    }

    /**
     * Donne le cookie de sélection de kit au joueur
     */
    public static void giveKitSelectorCookie(Player player) {
        ItemStack kitSelector = new ItemStack(Material.COOKIE);
        ItemMeta meta = kitSelector.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(KIT_SELECTOR_NAME);
            meta.setLore(java.util.List.of(
                    "§7Clic droit pour sélectionner votre kit",
                    "§7Choisissez parmi les kits disponibles",
                    "§7et leurs différents niveaux"));
            kitSelector.setItemMeta(meta);
        }

        player.getInventory().setItem(0, kitSelector); // Slot 1 (premier slot de la hotbar)
        player.sendMessage(Component.text("Utilisez le cookie pour sélectionner votre kit!")
                .color(NamedTextColor.YELLOW));
    }
}