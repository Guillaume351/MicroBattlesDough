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
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.api.GeyserApi;

import com.cookiebuild.cookiedough.model.MinigameStats;
import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.TieredKitManager;
import com.cookiebuild.microbattles.kits.TieredKitManager.KitLevel;
import com.cookiebuild.microbattles.kits.TieredKitManager.TieredKit;

public class TieredKitSelectionUI implements Listener {

    private final TieredKitManager tieredKitManager;
    private final MinigameStatsService statsService;
    private static final String JAVA_GUI_TITLE = ChatColor.DARK_AQUA + "Sélection de Kit - Niveaux";

    public TieredKitSelectionUI(TieredKitManager tieredKitManager, MinigameStatsService statsService) {
        this.tieredKitManager = tieredKitManager;
        this.statsService = statsService;
    }

    // --- Java Player GUI (Inventory) avec sections séparées ---
    public void openKitSelectionGUI(Player player) {
        UUID playerId = player.getUniqueId();
        MinigameStats playerStats = statsService.getOrCreateStats(playerId, MinigameStatsService.MICROBATTLES);

        // Récupérer tous les kits à niveaux + le kit Default
        List<TieredKit> tieredKits = tieredKitManager.getAllTieredKits();
        Kit defaultKit = tieredKitManager.getOriginalKitManager().getKit("Default");

        // Séparer les kits en catégories
        List<KitDisplayInfo> ownedKits = new ArrayList<>();
        List<KitDisplayInfo> availableKits = new ArrayList<>();
        List<KitDisplayInfo> lockedKits = new ArrayList<>();

        // Ajouter le kit Default
        if (defaultKit != null) {
            ownedKits.add(new KitDisplayInfo(defaultKit.getName(), 0, true, true, true, defaultKit));
        }

        // Traiter tous les kits à niveaux
        for (TieredKit tieredKit : tieredKits) {
            for (KitLevel level : tieredKit.getLevels()) {
                // Vérifier si le niveau précédent est débloqué (sauf pour le niveau 1)
                boolean previousLevelUnlocked = true;
                if (level.getLevel() > 1) {
                    previousLevelUnlocked = tieredKitManager.isKitLevelUnlocked(statsService, playerId,
                            tieredKit.getBaseName(), level.getLevel() - 1);
                }

                // Ne montrer ce niveau que si le précédent est débloqué
                if (!previousLevelUnlocked) {
                    continue; // Skip ce niveau
                }

                boolean unlocked = tieredKitManager.isKitLevelUnlocked(statsService, playerId, tieredKit.getBaseName(),
                        level.getLevel());
                boolean hasLevel = tieredKitManager.hasRequiredLevelForKit(statsService, playerId,
                        tieredKit.getBaseName(), level.getLevel());
                boolean canAfford = tieredKitManager.canAffordKitLevel(statsService, playerId, tieredKit.getBaseName(),
                        level.getLevel());

                KitDisplayInfo displayInfo = new KitDisplayInfo(
                        tieredKit.getBaseName(),
                        level.getLevel(),
                        unlocked,
                        hasLevel,
                        canAfford,
                        null,
                        tieredKit,
                        level);

                if (unlocked) {
                    ownedKits.add(displayInfo);
                } else if (hasLevel && canAfford) {
                    availableKits.add(displayInfo);
                } else {
                    lockedKits.add(displayInfo);
                }
            }
        }

        // Calculer la taille de l'inventaire
        int totalSlots = ownedKits.size() + availableKits.size() + lockedKits.size() + 6; // +6 pour headers et espaces
        int inventorySize = Math.max(27, (int) Math.ceil(totalSlots / 9.0) * 9);
        Inventory gui = Bukkit.createInventory(null, inventorySize, JAVA_GUI_TITLE);

        // Ajouter les informations du joueur en haut
        addPlayerInfoItems(gui, playerStats);

        int currentSlot = 9; // Commencer après la première ligne

        // Section des kits possédés
        if (!ownedKits.isEmpty()) {
            addSectionHeader(gui, currentSlot, ChatColor.GREEN + "✓ Kits Possédés", Material.EMERALD);
            currentSlot++;
            for (KitDisplayInfo kitInfo : ownedKits) {
                addKitItem(gui, currentSlot++, kitInfo);
            }
            currentSlot++; // Espace
        }

        // Section des kits disponibles à l'achat
        if (!availableKits.isEmpty()) {
            addSectionHeader(gui, currentSlot, ChatColor.YELLOW + "$ Disponibles à l'achat", Material.GOLD_INGOT);
            currentSlot++;
            for (KitDisplayInfo kitInfo : availableKits) {
                addKitItem(gui, currentSlot++, kitInfo);
            }
            currentSlot++; // Espace
        }

        // Section des kits verrouillés
        if (!lockedKits.isEmpty()) {
            addSectionHeader(gui, currentSlot, ChatColor.RED + "✗ Kits Verrouillés", Material.BARRIER);
            currentSlot++;
            for (KitDisplayInfo kitInfo : lockedKits) {
                addKitItem(gui, currentSlot++, kitInfo);
            }
        }

        player.openInventory(gui);
    }

    private void addPlayerInfoItems(Inventory gui, MinigameStats playerStats) {
        // Informations du joueur
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = playerInfo.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Vos Statistiques MicroBattles");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Niveau: " + playerStats.getLevel());
            lore.add(ChatColor.GOLD + "Pièces: " + playerStats.getCoins());
            lore.add(ChatColor.GREEN + "XP: " + playerStats.getExperience());
            lore.add("");
            lore.add(ChatColor.BLUE + "Victoires: " + playerStats.getWins());
            lore.add(ChatColor.RED + "Défaites: " + playerStats.getLosses());
            lore.add(ChatColor.GREEN + "Kills: " + playerStats.getKills());
            lore.add(ChatColor.DARK_RED + "Morts: " + playerStats.getDeaths());
            double kdRatio = playerStats.getDeaths() > 0 ? (double) playerStats.getKills() / playerStats.getDeaths()
                    : playerStats.getKills();
            lore.add(ChatColor.YELLOW + "K/D Ratio: " + String.format("%.2f", kdRatio));
            double winRate = (playerStats.getWins() + playerStats.getLosses()) > 0
                    ? (double) playerStats.getWins() / (playerStats.getWins() + playerStats.getLosses()) * 100
                    : 0;
            lore.add(ChatColor.AQUA + "Taux de victoire: " + String.format("%.1f%%", winRate));
            meta.setLore(lore);
            playerInfo.setItemMeta(meta);
        }
        gui.setItem(4, playerInfo);
    }

    /**
     * Retourne le matériau approprié pour représenter chaque kit
     */
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
            default:
                return Material.CHEST; // Fallback pour les kits non reconnus
        }
    }

    private void addSectionHeader(Inventory gui, int slot, String title, Material material) {
        if (slot >= gui.getSize())
            return;

        ItemStack header = new ItemStack(material);
        ItemMeta meta = header.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            meta.setLore(List.of(ChatColor.GRAY + "Section des kits"));
            header.setItemMeta(meta);
        }
        gui.setItem(slot, header);
    }

    private void addKitItem(Inventory gui, int slot, KitDisplayInfo kitInfo) {
        if (slot >= gui.getSize())
            return;

        // Utiliser le matériau approprié au kit, mais avec une couleur différente selon
        // le statut
        Material baseMaterial = getKitMaterial(kitInfo.kitName);
        Material iconMaterial;

        if (!kitInfo.unlocked && !kitInfo.hasLevel) {
            iconMaterial = Material.BARRIER; // Kit verrouillé par niveau
        } else if (!kitInfo.unlocked && !kitInfo.canAfford) {
            iconMaterial = Material.BARRIER; // Kit verrouillé par pièces
        } else {
            iconMaterial = baseMaterial; // Utiliser le matériau du kit
        }

        ItemStack kitItem = new ItemStack(iconMaterial);
        ItemMeta meta = kitItem.getItemMeta();
        if (meta != null) {
            String nameColor = kitInfo.unlocked ? ChatColor.GREEN.toString()
                    : (kitInfo.hasLevel && kitInfo.canAfford ? ChatColor.YELLOW.toString() : ChatColor.RED.toString());

            String displayName = kitInfo.level == 0 ? kitInfo.kitName
                    : kitInfo.kitName + " " + getRomanNumeral(kitInfo.level);
            meta.setDisplayName(ChatColor.RESET + nameColor + displayName);

            List<String> lore = new ArrayList<>();

            if (kitInfo.originalKit != null && kitInfo.originalKit.isDefaultUnlocked()) {
                lore.add(ChatColor.YELLOW + "Kit par défaut");
            } else if (kitInfo.unlocked) {
                lore.add(ChatColor.GREEN + "✓ Possédé");
            } else {
                lore.add(ChatColor.RED + "✗ Verrouillé");
                if (kitInfo.kitLevel != null) {
                    lore.add(ChatColor.GOLD + "Prix: " + kitInfo.kitLevel.getPrice() + " pièces");
                    if (kitInfo.kitLevel.getRequiredLevel() > 0) {
                        lore.add(ChatColor.AQUA + "Niveau requis: " + kitInfo.kitLevel.getRequiredLevel());
                    }
                }
            }

            lore.add(""); // Ligne vide

            // Description du kit
            String description = getKitDescription(kitInfo.kitName, kitInfo.level);
            if (description != null && !description.isEmpty()) {
                lore.add(ChatColor.DARK_GRAY + "--------------------");
                // Word wrap pour la description
                String[] words = description.split(" ");
                String currentLine = ChatColor.GRAY.toString();
                for (String word : words) {
                    if (currentLine.length() + word.length() + 1 > 40) {
                        lore.add(currentLine);
                        currentLine = ChatColor.GRAY.toString();
                    }
                    currentLine += word + " ";
                }
                lore.add(currentLine.trim());
                lore.add(ChatColor.DARK_GRAY + "--------------------");
                lore.add("");
            }

            if (kitInfo.unlocked) {
                lore.add(ChatColor.GREEN + "Clic pour sélectionner!");
            } else {
                if (!kitInfo.hasLevel) {
                    lore.add(ChatColor.RED + "Niveau "
                            + (kitInfo.kitLevel != null ? kitInfo.kitLevel.getRequiredLevel() : "?") + " requis");
                } else if (!kitInfo.canAfford) {
                    lore.add(ChatColor.RED + "Pas assez de pièces");
                } else {
                    lore.add(ChatColor.YELLOW + "Clic pour acheter!");
                }
            }
            meta.setLore(lore);
            kitItem.setItemMeta(meta);
        }
        gui.setItem(slot, kitItem);
    }

    private String getKitDescription(String kitName, int level) {
        // Descriptions basiques des kits avec indication du niveau
        String levelSuffix = level > 0 ? " (Niveau " + level + ")" : "";
        switch (kitName) {
            case "Default":
                return "Kit de base avec équipement standard.";
            case "Explosive Archer":
                return "Archer avec TNT explosif" + levelSuffix + ".";
            case "Enderman":
                return "Téléportation avec perles d'Ender" + levelSuffix + ".";
            case "Tank":
                return "Résistance et armure lourde" + levelSuffix + ".";
            case "Ninja":
                return "Vitesse et furtivité" + levelSuffix + ".";
            case "Archer":
                return "Spécialiste du tir à l'arc" + levelSuffix + ".";
            case "Berserker":
                return "Force brute et haches" + levelSuffix + ".";
            case "Chemist":
                return "Potions et effets chimiques" + levelSuffix + ".";
            case "Assassin":
                return "Vitesse et dégâts critiques" + levelSuffix + ".";
            case "Miner":
                return "Pioche et construction" + levelSuffix + ".";
            case "Vampire":
                return "Régénération et vol de vie" + levelSuffix + ".";
            case "Frost Mage":
                return "Magie de glace et ralentissement" + levelSuffix + ".";
            case "Juggernaut":
                return "Armure lourde et puissance" + levelSuffix + ".";
            case "Trapper":
                return "Pièges et embuscades" + levelSuffix + ".";
            case "Alchemist":
                return "Maître des potions" + levelSuffix + ".";
            default:
                return "Kit spécialisé" + levelSuffix + ".";
        }
    }

    private String getRomanNumeral(int number) {
        switch (number) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            default:
                return String.valueOf(number);
        }
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

        // Ignorer les clics sur les headers et les infos du joueur
        if (clickedItem.getType() == Material.EMERALD || clickedItem.getType() == Material.GOLD_INGOT ||
                clickedItem.getType() == Material.BARRIER || clickedItem.getType() == Material.PLAYER_HEAD) {
            return;
        }

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        UUID playerId = player.getUniqueId();

        // Parser le nom pour extraire le kit et le niveau
        String kitName;
        int level = 0;

        if (displayName.contains(" I")) {
            String[] parts = displayName.split(" I");
            kitName = parts[0];
            String romanLevel = "I" + (parts.length > 1 ? parts[1] : "");
            level = parseRomanNumeral(romanLevel);
        } else {
            kitName = displayName;
        }

        // Gérer le kit Default séparément
        if (kitName.equals("Default")) {
            tieredKitManager.selectKitLevel(playerId, "Default", 0);
            player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("ui.you_selected_kit",
                    java.util.Locale.ENGLISH, kitName));
            player.closeInventory();
            return;
        }

        // Vérifier si le kit à niveau est débloqué
        if (tieredKitManager.isKitLevelUnlocked(statsService, playerId, kitName, level)) {
            // Sélectionner le kit
            tieredKitManager.selectKitLevel(playerId, kitName, level);
            player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("ui.you_selected_kit",
                    java.util.Locale.ENGLISH, displayName));
            player.closeInventory();
        } else {
            // Tenter l'achat
            if (tieredKitManager.hasRequiredLevelForKit(statsService, playerId, kitName, level) &&
                    tieredKitManager.canAffordKitLevel(statsService, playerId, kitName, level)) {

                boolean purchaseSuccess = tieredKitManager.purchaseKitLevel(statsService, playerId, kitName, level);
                if (purchaseSuccess) {
                    player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("ui.kit_purchased_selected",
                            java.util.Locale.ENGLISH, displayName));
                    player.closeInventory();
                    openKitSelectionGUI(player); // Refresh GUI
                } else {
                    player.sendMessage(ChatColor.RED + LocaleManager.getMessage("ui.purchase_error",
                            java.util.Locale.ENGLISH));
                }
            } else if (!tieredKitManager.hasRequiredLevelForKit(statsService, playerId, kitName, level)) {
                TieredKit tieredKit = tieredKitManager.getTieredKit(kitName);
                if (tieredKit != null) {
                    KitLevel kitLevel = tieredKit.getLevel(level);
                    if (kitLevel != null) {
                        player.sendMessage(ChatColor.RED + LocaleManager.getMessage("ui.level_required",
                                java.util.Locale.ENGLISH, kitLevel.getRequiredLevel()));
                    }
                }
            } else {
                player.sendMessage(ChatColor.RED + LocaleManager.getMessage("ui.not_enough_coins",
                        java.util.Locale.ENGLISH));
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
                return 1;
        }
    }

    // --- Bedrock Player GUI (Cumulus Forms) ---
    public void openKitSelectionForm(Player player) {
        if (!GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
            openKitSelectionGUI(player);
            return;
        }

        UUID playerId = player.getUniqueId();
        MinigameStats playerStats = statsService.getOrCreateStats(playerId, MinigameStatsService.MICROBATTLES);

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("Sélection de Kit - Niveaux")
                .content(String.format(
                        "Niveau: %d | Pièces: %d | XP: %d\nVictoires: %d | Défaites: %d\n\n",
                        playerStats.getLevel(), playerStats.getCoins(),
                        playerStats.getExperience(),
                        playerStats.getWins(), playerStats.getLosses()));

        // Ajouter le kit Default
        Kit defaultKit = tieredKitManager.getOriginalKitManager().getKit("Default");
        if (defaultKit != null) {
            formBuilder.button("✓ " + defaultKit.getName() + " (Gratuit)");
        }

        // Ajouter tous les kits à niveaux
        List<TieredKit> tieredKits = tieredKitManager.getAllTieredKits();
        for (TieredKit tieredKit : tieredKits) {
            for (KitLevel level : tieredKit.getLevels()) {
                boolean unlocked = tieredKitManager.isKitLevelUnlocked(statsService, playerId, tieredKit.getBaseName(),
                        level.getLevel());
                boolean hasLevel = tieredKitManager.hasRequiredLevelForKit(statsService, playerId,
                        tieredKit.getBaseName(), level.getLevel());
                boolean canAfford = tieredKitManager.canAffordKitLevel(statsService, playerId, tieredKit.getBaseName(),
                        level.getLevel());

                String buttonText;
                String displayName = level.getDisplayName(tieredKit.getBaseName());

                if (unlocked) {
                    buttonText = "✓ " + displayName + " (Possédé)";
                } else if (hasLevel && canAfford) {
                    buttonText = "$ " + displayName + " (" + level.getPrice() + " pièces)";
                } else {
                    buttonText = "✗ " + displayName + " (";
                    if (!hasLevel) {
                        buttonText += "Niveau " + level.getRequiredLevel() + " requis";
                    } else {
                        buttonText += level.getPrice() + " pièces";
                    }
                    buttonText += ")";
                }
                formBuilder.button(buttonText);
            }
        }

        formBuilder.closedOrInvalidResultHandler(() -> {
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage("ui.selection_cancelled",
                    java.util.Locale.ENGLISH));
        });

        formBuilder.validResultHandler(response -> {
            // Logique de sélection pour Bedrock (simplifié pour l'instant)
            player.sendMessage(ChatColor.GREEN + "Kit sélectionné!");
        });

        GeyserApi.api().sendForm(player.getUniqueId(), formBuilder.build());
    }

    // Classe helper pour organiser les informations des kits
    private static class KitDisplayInfo {
        final String kitName;
        final int level;
        final boolean unlocked;
        final boolean hasLevel;
        final boolean canAfford;
        final Kit originalKit;
        final TieredKit tieredKit;
        final KitLevel kitLevel;

        KitDisplayInfo(String kitName, int level, boolean unlocked, boolean hasLevel, boolean canAfford,
                Kit originalKit) {
            this(kitName, level, unlocked, hasLevel, canAfford, originalKit, null, null);
        }

        KitDisplayInfo(String kitName, int level, boolean unlocked, boolean hasLevel, boolean canAfford,
                Kit originalKit, TieredKit tieredKit, KitLevel kitLevel) {
            this.kitName = kitName;
            this.level = level;
            this.unlocked = unlocked;
            this.hasLevel = hasLevel;
            this.canAfford = canAfford;
            this.originalKit = originalKit;
            this.tieredKit = tieredKit;
            this.kitLevel = kitLevel;
        }
    }
}