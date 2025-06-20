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

import com.cookiebuild.cookiedough.model.PlayerMinigameProgression;
import com.cookiebuild.cookiedough.service.PlayerMinigameProgressionService;
import com.cookiebuild.cookiedough.service.PlayerMinigameProgressionService.PlayerGameStats;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitManager;

public class ImprovedKitSelectionUI implements Listener {

    private final KitManager kitManager;
    private final PlayerMinigameProgressionService progressionService;
    private static final String JAVA_GUI_TITLE = ChatColor.DARK_AQUA + "Select Your Kit";

    public ImprovedKitSelectionUI(KitManager kitManager, PlayerMinigameProgressionService progressionService) {
        this.kitManager = kitManager;
        this.progressionService = progressionService;
    }

    // --- Java Player GUI (Inventory) avec sections séparées ---
    public void openKitSelectionGUI(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerMinigameProgression progression = progressionService.getOrCreateProgression(playerId,
                PlayerMinigameProgressionService.MICROBATTLES);
        PlayerGameStats gameStats = progressionService.calculateStats(playerId,
                PlayerMinigameProgressionService.MICROBATTLES);

        List<Kit> allKits = kitManager.getAllKits();
        // Trier tous les kits par niveau requis d'abord
        allKits.sort((k1, k2) -> Integer.compare(k1.getRequiredLevel(), k2.getRequiredLevel()));

        // Séparer les kits en catégories
        List<Kit> ownedKits = new ArrayList<>();
        List<Kit> availableKits = new ArrayList<>();
        List<Kit> lockedKits = new ArrayList<>();

        for (Kit kit : allKits) {
            boolean unlocked = progressionService.hasUnlockedKit(playerId,
                    PlayerMinigameProgressionService.MICROBATTLES, kit.getName()) || kit.isDefaultUnlocked();
            boolean hasLevel = progressionService.hasRequiredLevel(playerId,
                    PlayerMinigameProgressionService.MICROBATTLES, kit.getRequiredLevel());
            boolean canAfford = progressionService.canAffordKit(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                    kit.getPrice());

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
        addPlayerInfoItems(gui, progression, gameStats);

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
                boolean hasLevel = progressionService.hasRequiredLevel(playerId,
                        PlayerMinigameProgressionService.MICROBATTLES, kit.getRequiredLevel());
                boolean canAfford = progressionService.canAffordKit(playerId,
                        PlayerMinigameProgressionService.MICROBATTLES, kit.getPrice());
                addKitItem(gui, currentSlot++, kit, playerId, false, hasLevel, canAfford);
            }
            currentSlot++; // Espace
        }

        // Section des kits verrouillés
        if (!lockedKits.isEmpty()) {
            addSectionHeader(gui, currentSlot, ChatColor.RED + "✗ Kits Verrouillés", Material.BARRIER);
            currentSlot++;
            for (Kit kit : lockedKits) {
                boolean hasLevel = progressionService.hasRequiredLevel(playerId,
                        PlayerMinigameProgressionService.MICROBATTLES, kit.getRequiredLevel());
                boolean canAfford = progressionService.canAffordKit(playerId,
                        PlayerMinigameProgressionService.MICROBATTLES, kit.getPrice());
                addKitItem(gui, currentSlot++, kit, playerId, false, hasLevel, canAfford);
            }
        }

        player.openInventory(gui);
    }

    private void addPlayerInfoItems(Inventory gui, PlayerMinigameProgression progression, PlayerGameStats gameStats) {
        // Informations du joueur
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = playerInfo.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Vos Statistiques MicroBattles");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Niveau: " + progression.getLevel());
            lore.add(ChatColor.GOLD + "Pièces: " + progression.getCoins());
            lore.add(ChatColor.GREEN + "XP: " + progression.getExperience() + "/"
                    + progression.getExperienceForNextLevel());
            lore.add(ChatColor.GRAY + "XP pour niveau suivant: " + progression.getExperienceToNextLevel());
            lore.add("");
            lore.add(ChatColor.BLUE + "Victoires: " + gameStats.getWins());
            lore.add(ChatColor.RED + "Défaites: " + gameStats.getLosses());
            lore.add(ChatColor.GREEN + "Kills: " + gameStats.getKills());
            lore.add(ChatColor.DARK_RED + "Morts: " + gameStats.getDeaths());
            lore.add(ChatColor.YELLOW + "K/D Ratio: " + String.format("%.2f", gameStats.getKDRatio()));
            lore.add(ChatColor.AQUA + "Taux de victoire: " + String.format("%.1f%%", gameStats.getWinRate()));
            meta.setLore(lore);
            playerInfo.setItemMeta(meta);
        }
        gui.setItem(4, playerInfo);
    }

    /**
     * Retourne le matériau approprié pour représenter chaque kit
     */
    private Material getKitMaterial(Kit kit) {
        switch (kit.getName()) {
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

    private void addKitItem(Inventory gui, int slot, Kit kit, UUID playerId, boolean unlocked, boolean hasLevel,
            boolean canAfford) {
        if (slot >= gui.getSize())
            return;

        // Utiliser le matériau approprié au kit, mais avec une couleur différente selon
        // le statut
        Material baseMaterial = getKitMaterial(kit);
        Material iconMaterial;

        if (!unlocked && !hasLevel) {
            iconMaterial = Material.BARRIER; // Kit verrouillé par niveau
        } else if (!unlocked && !canAfford) {
            iconMaterial = Material.BARRIER; // Kit verrouillé par pièces
        } else {
            iconMaterial = baseMaterial; // Utiliser le matériau du kit
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

        if (progressionService.hasUnlockedKit(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                selectedKit.getName()) || selectedKit.isDefaultUnlocked()) {
            // Logique de sélection du kit
            storeKitSelection(player, selectedKit);
            player.sendMessage(ChatColor.GREEN + "Vous avez sélectionné le kit: " + selectedKit.getName());
            player.closeInventory();
            // TODO: Stocker le kit sélectionné pour la partie
        } else {
            if (progressionService.hasRequiredLevel(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                    selectedKit.getRequiredLevel())
                    && progressionService.canAffordKit(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                            selectedKit.getPrice())) {
                boolean purchaseSuccess = progressionService.purchaseKit(playerId,
                        PlayerMinigameProgressionService.MICROBATTLES, selectedKit.getName(), selectedKit.getPrice());
                if (purchaseSuccess) {
                    player.sendMessage(ChatColor.GREEN + "Kit " + selectedKit.getName() + " acheté et sélectionné!");
                    player.closeInventory();
                    openKitSelectionGUI(player); // Refresh GUI
                } else {
                    player.sendMessage(ChatColor.RED + "Erreur lors de l'achat.");
                }
            } else if (!progressionService.hasRequiredLevel(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                    selectedKit.getRequiredLevel())) {
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
        PlayerMinigameProgression progression = progressionService.getOrCreateProgression(playerId,
                PlayerMinigameProgressionService.MICROBATTLES);
        PlayerGameStats gameStats = progressionService.calculateStats(playerId,
                PlayerMinigameProgressionService.MICROBATTLES);

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("Sélection de Kit - MicroBattles")
                .content(String.format(
                        "Niveau: %d | Pièces: %d | XP: %d/%d\nVictoires: %d | Défaites: %d | K/D: %.2f\n\n",
                        progression.getLevel(), progression.getCoins(),
                        progression.getExperience(), progression.getExperienceForNextLevel(),
                        gameStats.getWins(), gameStats.getLosses(), gameStats.getKDRatio()));

        List<Kit> allKits = kitManager.getAllKits();
        // Trier les kits par niveau requis
        allKits.sort((k1, k2) -> Integer.compare(k1.getRequiredLevel(), k2.getRequiredLevel()));

        // Séparer et ajouter les kits par sections
        List<Kit> ownedKits = new ArrayList<>();
        List<Kit> availableKits = new ArrayList<>();
        List<Kit> lockedKits = new ArrayList<>();

        for (Kit kit : allKits) {
            boolean unlocked = progressionService.hasUnlockedKit(playerId,
                    PlayerMinigameProgressionService.MICROBATTLES, kit.getName()) || kit.isDefaultUnlocked();
            boolean hasLevel = progressionService.hasRequiredLevel(playerId,
                    PlayerMinigameProgressionService.MICROBATTLES, kit.getRequiredLevel());
            boolean canAfford = progressionService.canAffordKit(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                    kit.getPrice());

            if (unlocked) {
                ownedKits.add(kit);
            } else if (hasLevel && canAfford) {
                availableKits.add(kit);
            } else {
                lockedKits.add(kit);
            }
        }

        // Les kits sont déjà triés par niveau requis depuis allKits

        // Ajouter les boutons par sections
        for (Kit kit : ownedKits) {
            formBuilder.button("✓ " + kit.getName() + " (Possédé)");
        }
        for (Kit kit : availableKits) {
            formBuilder.button("$ " + kit.getName() + " (" + kit.getPrice() + " pièces)");
        }
        for (Kit kit : lockedKits) {
            String buttonText = "✗ " + kit.getName() + " (";
            if (!progressionService.hasRequiredLevel(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                    kit.getRequiredLevel())) {
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

            if (progressionService.hasUnlockedKit(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                    selectedKit.getName()) || selectedKit.isDefaultUnlocked()) {
                storeKitSelection(player, selectedKit);
                player.sendMessage(ChatColor.GREEN + "Vous avez sélectionné le kit: " + selectedKit.getName());
            } else {
                if (progressionService.hasRequiredLevel(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                        selectedKit.getRequiredLevel())
                        && progressionService.canAffordKit(playerId, PlayerMinigameProgressionService.MICROBATTLES,
                                selectedKit.getPrice())) {
                    boolean purchaseSuccess = progressionService.purchaseKit(playerId,
                            PlayerMinigameProgressionService.MICROBATTLES, selectedKit.getName(),
                            selectedKit.getPrice());
                    if (purchaseSuccess) {
                        player.sendMessage(
                                ChatColor.GREEN + "Kit " + selectedKit.getName() + " acheté et sélectionné!");
                        openKitSelectionForm(player); // Refresh form
                    } else {
                        player.sendMessage(ChatColor.RED + "Impossible d'acheter le kit.");
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