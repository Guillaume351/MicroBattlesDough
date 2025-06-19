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
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitManager;

public class ImprovedKitSelectionUI implements Listener {

    private final KitManager kitManager;
    private final MinigameStatsService statsService;
    private static final String JAVA_GUI_TITLE = ChatColor.DARK_AQUA + "Select Your Kit";

    public ImprovedKitSelectionUI(KitManager kitManager, MinigameStatsService statsService) {
        this.kitManager = kitManager;
        this.statsService = statsService;
    }

    // --- Java Player GUI (Inventory) avec sections séparées ---
    public void openKitSelectionGUI(Player player) {
        UUID playerId = player.getUniqueId();
        MinigameStats playerStats = statsService.getOrCreateStats(playerId, MinigameStatsService.MICROBATTLES);

        List<Kit> allKits = kitManager.getAllKits();

        // Séparer les kits en catégories
        List<Kit> ownedKits = new ArrayList<>();
        List<Kit> availableKits = new ArrayList<>();
        List<Kit> lockedKits = new ArrayList<>();

        for (Kit kit : allKits) {
            boolean unlocked = kitManager.isKitUnlocked(statsService, playerId, kit);
            boolean hasLevel = kitManager.hasRequiredLevelForKit(statsService, playerId, kit);
            boolean canAfford = kitManager.canAffordKit(statsService, playerId, kit);

            if (unlocked) {
                ownedKits.add(kit);
            } else if (hasLevel && canAfford) {
                availableKits.add(kit);
            } else {
                lockedKits.add(kit);
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
            for (Kit kit : ownedKits) {
                addKitItem(gui, currentSlot++, kit, playerId, true, true, true);
            }
            currentSlot++; // Espace
        }

        // Section des kits disponibles à l'achat
        if (!availableKits.isEmpty()) {
            addSectionHeader(gui, currentSlot, ChatColor.YELLOW + "$ Disponibles à l'achat", Material.GOLD_INGOT);
            currentSlot++;
            for (Kit kit : availableKits) {
                boolean hasLevel = kitManager.hasRequiredLevelForKit(statsService, playerId, kit);
                boolean canAfford = kitManager.canAffordKit(statsService, playerId, kit);
                addKitItem(gui, currentSlot++, kit, playerId, false, hasLevel, canAfford);
            }
            currentSlot++; // Espace
        }

        // Section des kits verrouillés
        if (!lockedKits.isEmpty()) {
            addSectionHeader(gui, currentSlot, ChatColor.RED + "✗ Kits Verrouillés", Material.BARRIER);
            currentSlot++;
            for (Kit kit : lockedKits) {
                boolean hasLevel = kitManager.hasRequiredLevelForKit(statsService, playerId, kit);
                boolean canAfford = kitManager.canAffordKit(statsService, playerId, kit);
                addKitItem(gui, currentSlot++, kit, playerId, false, hasLevel, canAfford);
            }
        }

        player.openInventory(gui);
    }

    private void addPlayerInfoItems(Inventory gui, MinigameStats stats) {
        // Informations du joueur
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = playerInfo.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Vos Statistiques MicroBattles");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Niveau: " + stats.getLevel());
            lore.add(ChatColor.GOLD + "Pièces: " + stats.getCoins());
            lore.add(ChatColor.GREEN + "XP: " + stats.getExperience() + "/" + stats.getExperienceForNextLevel());
            lore.add(ChatColor.GRAY + "XP pour niveau suivant: " + stats.getExperienceToNextLevel());
            lore.add("");
            lore.add(ChatColor.BLUE + "Victoires: " + stats.getWins());
            lore.add(ChatColor.RED + "Défaites: " + stats.getLosses());
            lore.add(ChatColor.GREEN + "Kills: " + stats.getKills());
            lore.add(ChatColor.DARK_RED + "Morts: " + stats.getDeaths());
            lore.add(ChatColor.YELLOW + "K/D Ratio: " + String.format("%.2f", stats.getKDRatio()));
            lore.add(ChatColor.AQUA + "Taux de victoire: " + String.format("%.1f%%", stats.getWinRate()));
            meta.setLore(lore);
            playerInfo.setItemMeta(meta);
        }
        gui.setItem(4, playerInfo);
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

    private void addKitItem(Inventory gui, int slot, Kit kit, UUID playerId, boolean unlocked, boolean hasLevel,
            boolean canAfford) {
        if (slot >= gui.getSize())
            return;

        Material iconMaterial;
        if (unlocked) {
            iconMaterial = Material.CHEST; // Kit possédé
        } else if (hasLevel && canAfford) {
            iconMaterial = Material.ENDER_CHEST; // Kit achetable
        } else {
            iconMaterial = Material.BARRIER; // Kit verrouillé
        }

        ItemStack kitItem = new ItemStack(iconMaterial);
        ItemMeta meta = kitItem.getItemMeta();
        if (meta != null) {
            String nameColor = unlocked ? ChatColor.GREEN.toString()
                    : (hasLevel && canAfford ? ChatColor.YELLOW.toString() : ChatColor.RED.toString());
            meta.setDisplayName(ChatColor.RESET + nameColor + kit.getName());

            List<String> lore = new ArrayList<>();

            if (kit.isDefaultUnlocked()) {
                lore.add(ChatColor.YELLOW + "Kit par défaut");
            } else if (unlocked) {
                lore.add(ChatColor.GREEN + "✓ Possédé");
            } else {
                lore.add(ChatColor.RED + "✗ Verrouillé");
                lore.add(ChatColor.GOLD + "Prix: " + kit.getPrice() + " pièces");
                if (kit.getRequiredLevel() > 0) {
                    lore.add(ChatColor.AQUA + "Niveau requis: " + kit.getRequiredLevel());
                }
            }

            lore.add(""); // Ligne vide
            if (kit.getDescription() != null && !kit.getDescription().isEmpty()) {
                lore.add(ChatColor.DARK_GRAY + "--------------------");
                // Word wrap pour la description
                String[] words = kit.getDescription().split(" ");
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

            if (unlocked) {
                lore.add(ChatColor.GREEN + "Clic pour sélectionner!");
            } else {
                if (!hasLevel) {
                    lore.add(ChatColor.RED + "Niveau " + kit.getRequiredLevel() + " requis");
                } else if (!canAfford) {
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

        String kitName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        Kit selectedKit = kitManager.getKit(kitName);

        if (selectedKit == null)
            return;

        UUID playerId = player.getUniqueId();

        if (kitManager.isKitUnlocked(statsService, playerId, selectedKit)) {
            // Logique de sélection du kit
            storeKitSelection(player, selectedKit);
            player.sendMessage(ChatColor.GREEN + "Vous avez sélectionné le kit: " + selectedKit.getName());
            player.closeInventory();
            // TODO: Stocker le kit sélectionné pour la partie
        } else {
            if (kitManager.hasRequiredLevelForKit(statsService, playerId, selectedKit)
                    && kitManager.canAffordKit(statsService, playerId, selectedKit)) {
                KitManager.PurchaseResult result = kitManager.purchaseKit(statsService, playerId, selectedKit);
                switch (result) {
                    case SUCCESS:
                        player.sendMessage(
                                ChatColor.GREEN + "Kit " + selectedKit.getName() + " acheté et sélectionné!");
                        player.closeInventory();
                        openKitSelectionGUI(player); // Refresh GUI
                        break;
                    case NOT_ENOUGH_COINS:
                        player.sendMessage(ChatColor.RED + "Vous n'avez pas assez de pièces.");
                        break;
                    case LEVEL_TOO_LOW:
                        player.sendMessage(ChatColor.RED + "Votre niveau est trop bas.");
                        break;
                    case ALREADY_UNLOCKED:
                        player.sendMessage(ChatColor.YELLOW + "Vous possédez déjà ce kit.");
                        break;
                    case ERROR:
                        player.sendMessage(ChatColor.RED + "Erreur lors de l'achat.");
                        break;
                }
            } else if (!kitManager.hasRequiredLevelForKit(statsService, playerId, selectedKit)) {
                player.sendMessage(ChatColor.RED + "Niveau " + selectedKit.getRequiredLevel() + " requis.");
            } else {
                player.sendMessage(ChatColor.RED + "Pas assez de pièces.");
            }
        }
    }

    // --- Bedrock Player GUI (Cumulus Forms) avec sections séparées ---
    public void openKitSelectionForm(Player player) {
        if (!GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
            openKitSelectionGUI(player);
            return;
        }

        UUID playerId = player.getUniqueId();
        MinigameStats playerStats = statsService.getOrCreateStats(playerId, MinigameStatsService.MICROBATTLES);

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("Sélection de Kit - MicroBattles")
                .content(String.format("Niveau: %d | Pièces: %d | XP: %d/%d\n\n",
                        playerStats.getLevel(), playerStats.getCoins(),
                        playerStats.getExperience(), playerStats.getExperienceForNextLevel()));

        List<Kit> allKits = kitManager.getAllKits();

        // Séparer et ajouter les kits par sections
        List<Kit> ownedKits = new ArrayList<>();
        List<Kit> availableKits = new ArrayList<>();
        List<Kit> lockedKits = new ArrayList<>();

        for (Kit kit : allKits) {
            boolean unlocked = kitManager.isKitUnlocked(statsService, playerId, kit);
            boolean hasLevel = kitManager.hasRequiredLevelForKit(statsService, playerId, kit);
            boolean canAfford = kitManager.canAffordKit(statsService, playerId, kit);

            if (unlocked) {
                ownedKits.add(kit);
            } else if (hasLevel && canAfford) {
                availableKits.add(kit);
            } else {
                lockedKits.add(kit);
            }
        }

        // Ajouter les boutons par sections
        for (Kit kit : ownedKits) {
            formBuilder.button("✓ " + kit.getName() + " (Possédé)");
        }
        for (Kit kit : availableKits) {
            formBuilder.button("$ " + kit.getName() + " (" + kit.getPrice() + " pièces)");
        }
        for (Kit kit : lockedKits) {
            String buttonText = "✗ " + kit.getName() + " (";
            if (!kitManager.hasRequiredLevelForKit(statsService, playerId, kit)) {
                buttonText += "Niveau " + kit.getRequiredLevel() + " requis";
            } else {
                buttonText += kit.getPrice() + " pièces";
            }
            buttonText += ")";
            formBuilder.button(buttonText);
        }

        formBuilder.closedOrInvalidResultHandler(() -> {
            player.sendMessage(ChatColor.YELLOW + "Sélection de kit annulée.");
        });

        formBuilder.validResultHandler(response -> {
            Kit selectedKit = allKits.get(response.clickedButtonId());

            if (kitManager.isKitUnlocked(statsService, playerId, selectedKit)) {
                storeKitSelection(player, selectedKit);
                player.sendMessage(ChatColor.GREEN + "Vous avez sélectionné le kit: " + selectedKit.getName());
            } else {
                if (kitManager.hasRequiredLevelForKit(statsService, playerId, selectedKit)
                        && kitManager.canAffordKit(statsService, playerId, selectedKit)) {
                    KitManager.PurchaseResult result = kitManager.purchaseKit(statsService, playerId, selectedKit);
                    if (result == KitManager.PurchaseResult.SUCCESS) {
                        player.sendMessage(
                                ChatColor.GREEN + "Kit " + selectedKit.getName() + " acheté et sélectionné!");
                        openKitSelectionForm(player); // Refresh form
                    } else {
                        player.sendMessage(ChatColor.RED + "Impossible d'acheter le kit. Raison: " + result.toString());
                        openKitSelectionForm(player);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Vous ne pouvez pas acheter ce kit.");
                    openKitSelectionForm(player);
                }
            }
        });

        GeyserApi.api().sendForm(player.getUniqueId(), formBuilder.build());
    }

    /**
     * Stocke la sélection de kit du joueur dans le jeu MicroBattles
     */
    private void storeKitSelection(Player player, Kit selectedKit) {
        // Pour l'instant, on stocke la sélection dans un message au joueur
        // L'intégration complète avec MicroBattlesGame sera faite plus tard
        player.sendMessage(ChatColor.GRAY + "Kit " + selectedKit.getName() + " sélectionné pour la prochaine partie !");
    }
}