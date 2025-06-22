package com.cookiebuild.microbattles.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.cookiebuild.cookiedough.model.MinigameStats;
import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitLevel;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.kits.TieredKit;

public class KitSelectionUI implements Listener {

    private final KitManager kitManager;
    private final MinigameStatsService statsService;
    private static final String JAVA_GUI_TITLE = ChatColor.DARK_AQUA + "Kit Selection";

    public KitSelectionUI(KitManager kitManager, MinigameStatsService statsService) {
        this.kitManager = kitManager;
        this.statsService = statsService;
    }

    public void openKitSelectionGUI(Player player) {
        UUID playerId = player.getUniqueId();
        MinigameStats playerStats = statsService.getOrCreateStats(playerId, MinigameStatsService.MICROBATTLES);

        List<TieredKit> tieredKits = new ArrayList<>(kitManager.getAllTieredKits());
        Kit defaultKit = kitManager.getOriginalKit("Default");

        List<KitDisplayInfo> ownedKits = new ArrayList<>();
        List<KitDisplayInfo> availableKits = new ArrayList<>();
        List<KitDisplayInfo> lockedKits = new ArrayList<>();

        if (defaultKit != null) {
            ownedKits.add(new KitDisplayInfo(defaultKit.getName(), 0, true, true, true, defaultKit));
        }

        for (TieredKit tieredKit : tieredKits) {
            for (KitLevel level : tieredKit.getLevels()) {
                boolean previousLevelUnlocked = level.getLevel() == 1 || kitManager.isKitLevelUnlocked(statsService,
                        playerId, tieredKit.getBaseName(), level.getLevel() - 1);
                if (!previousLevelUnlocked) {
                    continue;
                }

                boolean unlocked = kitManager.isKitLevelUnlocked(statsService, playerId, tieredKit.getBaseName(),
                        level.getLevel());
                boolean hasLevel = kitManager.hasRequiredPlayerLevelForKit(statsService, playerId,
                        tieredKit.getBaseName(), level.getLevel());
                boolean canAfford = kitManager.canAffordKitLevel(statsService, playerId, tieredKit.getBaseName(),
                        level.getLevel());

                KitDisplayInfo displayInfo = new KitDisplayInfo(tieredKit.getBaseName(), level.getLevel(), unlocked,
                        hasLevel, canAfford, tieredKit, level);

                if (unlocked) {
                    ownedKits.add(displayInfo);
                } else if (hasLevel && canAfford) {
                    availableKits.add(displayInfo);
                } else {
                    lockedKits.add(displayInfo);
                }
            }
        }

        int totalSlots = ownedKits.size() + availableKits.size() + lockedKits.size() + 6;
        int inventorySize = Math.max(27, (int) Math.ceil(totalSlots / 9.0) * 9);
        Inventory gui = Bukkit.createInventory(null, inventorySize, JAVA_GUI_TITLE);

        addPlayerInfoItems(gui, playerStats);

        int currentSlot = 9;
        if (!ownedKits.isEmpty()) {
            addSectionHeader(gui, currentSlot, ChatColor.GREEN + "✓ Owned Kits", Material.EMERALD);
            currentSlot++;
            for (KitDisplayInfo kitInfo : ownedKits) {
                addKitItem(gui, currentSlot++, kitInfo);
            }
            currentSlot++;
        }
        if (!availableKits.isEmpty()) {
            addSectionHeader(gui, currentSlot, ChatColor.YELLOW + "$ Available for Purchase", Material.GOLD_INGOT);
            currentSlot++;
            for (KitDisplayInfo kitInfo : availableKits) {
                addKitItem(gui, currentSlot++, kitInfo);
            }
            currentSlot++;
        }
        if (!lockedKits.isEmpty()) {
            addSectionHeader(gui, currentSlot, ChatColor.RED + "✗ Locked Kits", Material.BARRIER);
            currentSlot++;
            for (KitDisplayInfo kitInfo : lockedKits) {
                addKitItem(gui, currentSlot++, kitInfo);
            }
        }

        player.openInventory(gui);
    }

    private void addPlayerInfoItems(Inventory gui, MinigameStats playerStats) {
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = playerInfo.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Your MicroBattles Stats");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Level: " + playerStats.getLevel());
            lore.add(ChatColor.GOLD + "Coins: " + playerStats.getCoins());
            lore.add(ChatColor.GREEN + "XP: " + playerStats.getExperience());
            lore.add("");
            lore.add(ChatColor.BLUE + "Wins: " + playerStats.getWins());
            lore.add(ChatColor.RED + "Losses: " + playerStats.getLosses());
            lore.add(ChatColor.GREEN + "Kills: " + playerStats.getKills());
            lore.add(ChatColor.DARK_RED + "Deaths: " + playerStats.getDeaths());
            double kdRatio = playerStats.getDeaths() > 0 ? (double) playerStats.getKills() / playerStats.getDeaths()
                    : playerStats.getKills();
            lore.add(ChatColor.YELLOW + "K/D Ratio: " + String.format("%.2f", kdRatio));
            double winRate = (playerStats.getWins() + playerStats.getLosses()) > 0
                    ? (double) playerStats.getWins() / (playerStats.getWins() + playerStats.getLosses()) * 100
                    : 0;
            lore.add(ChatColor.AQUA + "Win Rate: " + String.format("%.1f%%", winRate));
            meta.setLore(lore);
            playerInfo.setItemMeta(meta);
        }
        gui.setItem(4, playerInfo);
    }

    private Material getKitMaterial(String kitName) {
        switch (kitName) {
            case "Default":
                return Material.STONE_SWORD;
            case "Archer":
                return Material.BOW;
            case "Miner":
                return Material.DIAMOND_PICKAXE;
            case "Trapper":
                return Material.TRIPWIRE_HOOK;
            case "Knockback Warrior":
                return Material.STICK;
            case "Berserker":
                return Material.DIAMOND_AXE;
            case "Explosive Archer":
                return Material.TNT;
            case "Alchemist":
                return Material.BREWING_STAND;
            case "Vampire":
                return Material.IRON_SWORD;
            case "Tank":
                return Material.SHIELD;
            case "Enderman":
                return Material.ENDER_PEARL;
            case "Ninja":
                return Material.LEATHER_BOOTS;
            case "Chemist":
                return Material.SPLASH_POTION;
            case "Frost Mage":
                return Material.SNOWBALL;
            case "Assassin":
                return Material.GOLDEN_SWORD;
            case "Juggernaut":
                return Material.NETHERITE_AXE;
            case "Mobility":
                return Material.FEATHER;
            default:
                return Material.CHEST;
        }
    }

    private void addSectionHeader(Inventory gui, int slot, String title, Material material) {
        if (slot >= gui.getSize())
            return;
        ItemStack header = new ItemStack(material);
        ItemMeta meta = header.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            header.setItemMeta(meta);
        }
        gui.setItem(slot, header);
    }

    private void addKitItem(Inventory gui, int slot, KitDisplayInfo kitInfo) {
        if (slot >= gui.getSize())
            return;

        Material iconMaterial = getKitMaterial(kitInfo.kitName);
        if (!kitInfo.unlocked) {
            iconMaterial = Material.BARRIER;
        }

        ItemStack kitItem = new ItemStack(iconMaterial);
        ItemMeta meta = kitItem.getItemMeta();
        if (meta != null) {
            String nameColor = kitInfo.unlocked ? ChatColor.GREEN.toString()
                    : (kitInfo.hasLevel && kitInfo.canAfford ? ChatColor.YELLOW.toString() : ChatColor.RED.toString());
            String displayName = kitInfo.level == 0 ? kitInfo.kitName
                    : kitInfo.kitLevel.getDisplayName(kitInfo.kitName);
            meta.setDisplayName(ChatColor.RESET + nameColor + displayName);

            List<String> lore = new ArrayList<>();
            if (kitInfo.originalKit != null && kitInfo.originalKit.isDefaultUnlocked()) {
                lore.add(ChatColor.YELLOW + "Default Kit");
            } else if (kitInfo.unlocked) {
                lore.add(ChatColor.GREEN + "✓ Owned");
            } else {
                lore.add(ChatColor.RED + "✗ Locked");
                if (kitInfo.kitLevel != null) {
                    lore.add(ChatColor.GOLD + "Price: " + kitInfo.kitLevel.getPrice() + " coins");
                    if (kitInfo.kitLevel.getRequiredLevel() > 0) {
                        lore.add(ChatColor.AQUA + "Required Level: " + kitInfo.kitLevel.getRequiredLevel());
                    }
                }
            }
            lore.add("");

            String description = kitManager.getOriginalKit(kitInfo.kitName).getDescription();
            if (description != null && !description.isEmpty()) {
                lore.add(ChatColor.DARK_GRAY + "--------------------");
                lore.add(ChatColor.GRAY + description);
                lore.add(ChatColor.DARK_GRAY + "--------------------");
                lore.add("");
            }

            if (kitInfo.unlocked) {
                lore.add(ChatColor.GREEN + "Click to select!");
            } else {
                if (!kitInfo.hasLevel) {
                    lore.add(ChatColor.RED + "Level "
                            + (kitInfo.kitLevel != null ? kitInfo.kitLevel.getRequiredLevel() : "?") + " required");
                } else if (!kitInfo.canAfford) {
                    lore.add(ChatColor.RED + "Not enough coins");
                } else {
                    lore.add(ChatColor.YELLOW + "Click to purchase!");
                }
            }
            meta.setLore(lore);
            kitItem.setItemMeta(meta);
        }
        gui.setItem(slot, kitItem);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(JAVA_GUI_TITLE)) {
            return;
        }
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) {
            return;
        }

        if (clickedItem.getType() == Material.EMERALD || clickedItem.getType() == Material.GOLD_INGOT
                || clickedItem.getType() == Material.BARRIER || clickedItem.getType() == Material.PLAYER_HEAD) {
            return;
        }

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        UUID playerId = player.getUniqueId();

        String kitName;
        int level = 0;

        String[] parts = displayName.split(" ");
        if (parts.length > 1) {
            String lastPart = parts[parts.length - 1];
            level = parseRomanNumeral(lastPart);
            if (level > 0) {
                kitName = displayName.substring(0, displayName.lastIndexOf(" "));
            } else {
                kitName = displayName;
            }
        } else {
            kitName = displayName;
        }

        if (kitName.equals("Default")) {
            kitManager.selectKit(playerId, "Default", 0);
            player.sendMessage(ChatColor.GREEN + "You selected the Default kit.");
            player.closeInventory();
            return;
        }

        if (kitManager.isKitLevelUnlocked(statsService, playerId, kitName, level)) {
            kitManager.selectKit(playerId, kitName, level);
            player.sendMessage(ChatColor.GREEN + "You selected " + displayName);
            player.closeInventory();
        } else {
            if (kitManager.hasRequiredPlayerLevelForKit(statsService, playerId, kitName, level)
                    && kitManager.canAffordKitLevel(statsService, playerId, kitName, level)) {
                boolean purchaseSuccess = kitManager.purchaseKitLevel(statsService, playerId, kitName, level);
                if (purchaseSuccess) {
                    player.sendMessage(ChatColor.GREEN + "Successfully purchased and selected " + displayName);
                    player.closeInventory();
                    openKitSelectionGUI(player); // Refresh
                } else {
                    player.sendMessage(ChatColor.RED + "An error occurred during purchase.");
                }
            } else if (!kitManager.hasRequiredPlayerLevelForKit(statsService, playerId, kitName, level)) {
                KitLevel kitLevel = kitManager.getTieredKit(kitName).getLevel(level);
                player.sendMessage(
                        ChatColor.RED + "You need to be level " + kitLevel.getRequiredLevel() + " to purchase this.");
            } else {
                player.sendMessage(ChatColor.RED + "You don't have enough coins.");
            }
        }
    }

    private int parseRomanNumeral(String roman) {
        switch (roman) {
            case "I":
                return 1;
            case "II":
                return 2;
            case "III":
                return 3;
            default:
                return 0;
        }
    }

    // Bedrock support can be re-added later if needed, focusing on Java first for
    // cleanup.
}