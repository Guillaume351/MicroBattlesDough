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
import org.geysermc.cumulus.form.ModalForm; // Ajout de l'import
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.api.GeyserApi;

import com.cookiebuild.cookiedough.model.MinigameStats;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitManager;

public class KitSelectionUI implements Listener {

    private final KitManager kitManager;
    private final MinigameStatsService statsService;
    private static final String JAVA_GUI_TITLE = ChatColor.DARK_AQUA + "Select Your Kit";

    public KitSelectionUI(KitManager kitManager, MinigameStatsService statsService) {
        this.kitManager = kitManager;
        this.statsService = statsService;
    }

    // --- Java Player GUI (Inventory) ---
    public void openKitSelectionGUI(Player player) {
        UUID playerId = player.getUniqueId();
        MinigameStats playerStats = statsService.getOrCreateStats(playerId, MinigameStatsService.MICROBATTLES);

        List<Kit> allKits = kitManager.getAllKits();
        int inventorySize = (int) Math.ceil(allKits.size() / 9.0) * 9;
        if (inventorySize == 0)
            inventorySize = 9; // Minimum size
        Inventory gui = Bukkit.createInventory(null, inventorySize, JAVA_GUI_TITLE);

        for (Kit kit : allKits) {
            ItemStack kitItem = new ItemStack(Material.CHEST); // Icône par défaut, à personnaliser
            ItemMeta meta = kitItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RESET + "" + ChatColor.GREEN + kit.getName());
                List<String> lore = new ArrayList<>();

                boolean unlocked = kitManager.isKitUnlocked(statsService, playerId, kit);
                boolean canAfford = kitManager.canAffordKit(statsService, playerId, kit);
                boolean hasLevel = kitManager.hasRequiredLevelForKit(statsService, playerId, kit);

                if (kit.isDefaultUnlocked()) {
                    lore.add(ChatColor.YELLOW + "Default Kit");
                } else if (unlocked) {
                    lore.add(ChatColor.GREEN + "Unlocked");
                } else {
                    lore.add(ChatColor.RED + "Locked");
                    lore.add(ChatColor.GOLD + "Price: " + kit.getPrice() + " coins");
                    if (kit.getRequiredLevel() > 0) {
                        lore.add(ChatColor.AQUA + "Required Level: " + kit.getRequiredLevel());
                    }
                }
                lore.add(""); // Ligne vide
                if (kit.getDescription() != null && !kit.getDescription().isEmpty()) {
                    lore.add(ChatColor.DARK_GRAY + "--------------------");
                    // Simple word wrap for description
                    String[] words = kit.getDescription().split(" ");
                    String currentLine = ChatColor.GRAY.toString();
                    for (String word : words) {
                        if (currentLine.length() + word.length() + 1 > 40) { // Max line length (approx)
                            lore.add(currentLine);
                            currentLine = ChatColor.GRAY.toString();
                        }
                        currentLine += word + " ";
                    }
                    lore.add(currentLine.trim());
                    lore.add(ChatColor.DARK_GRAY + "--------------------");
                    lore.add(""); // Ligne vide
                }

                if (unlocked) {
                    lore.add(ChatColor.GRAY + "Click to select!");
                } else {
                    if (!hasLevel) {
                        lore.add(ChatColor.RED + "You need level " + kit.getRequiredLevel() + " to purchase.");
                    } else if (!canAfford) {
                        lore.add(ChatColor.RED + "You need " + kit.getPrice() + " coins to purchase.");
                    } else {
                        lore.add(ChatColor.YELLOW + "Click to purchase!");
                    }
                }
                meta.setLore(lore);
                kitItem.setItemMeta(meta);
            }
            gui.addItem(kitItem);
        }
        player.openInventory(gui);
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

        String kitName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        Kit selectedKit = kitManager.getKit(kitName);

        if (selectedKit == null)
            return;

        UUID playerId = player.getUniqueId();

        if (kitManager.isKitUnlocked(statsService, playerId, selectedKit)) {
            // Logique de sélection du kit (par exemple, stocker le choix du joueur)
            player.sendMessage(ChatColor.GREEN + "You selected kit: " + selectedKit.getName());
            // kitManager.selectKit(playerData, selectedKit); // Déplacer cette logique dans
            // le jeu
            player.closeInventory();
            // Équiper le kit ou le marquer pour la prochaine partie
        } else {
            if (kitManager.hasRequiredLevelForKit(statsService, playerId, selectedKit)
                    && kitManager.canAffordKit(statsService, playerId, selectedKit)) {
                KitManager.PurchaseResult result = kitManager.purchaseKit(statsService, playerId, selectedKit);
                switch (result) {
                    case SUCCESS:
                        player.sendMessage(
                                ChatColor.GREEN + "Kit " + selectedKit.getName() + " purchased and selected!");
                        // playerDataService.savePlayerData(playerData); // IMPORTANT: Sauvegarder les
                        // données
                        // kitManager.selectKit(playerData, selectedKit);
                        player.closeInventory();
                        openKitSelectionGUI(player); // Refresh GUI
                        break;
                    case NOT_ENOUGH_COINS:
                        player.sendMessage(ChatColor.RED + "You don't have enough coins to purchase this kit.");
                        break;
                    case LEVEL_TOO_LOW:
                        player.sendMessage(ChatColor.RED + "You don't have the required level to purchase this kit.");
                        break;
                    case ALREADY_UNLOCKED: // Ne devrait pas arriver ici si la logique est correcte
                        player.sendMessage(ChatColor.YELLOW + "You have already unlocked this kit.");
                        break;
                    case ERROR:
                        player.sendMessage(ChatColor.RED + "An error occurred while purchasing the kit.");
                        break;
                }
            } else if (!kitManager.hasRequiredLevelForKit(statsService, playerId, selectedKit)) {
                player.sendMessage(
                        ChatColor.RED + "You need level " + selectedKit.getRequiredLevel() + " to purchase this kit.");
            } else {
                player.sendMessage(ChatColor.RED + "You cannot afford this kit.");
            }
        }
    }

    // --- Bedrock Player GUI (Cumulus Forms) ---
    public void openKitSelectionForm(Player player) {
        if (!GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
            openKitSelectionGUI(player); // Fallback pour les joueurs Java si appelés par erreur
            return;
        }

        UUID playerId = player.getUniqueId();
        MinigameStats playerStats = statsService.getOrCreateStats(playerId, MinigameStatsService.MICROBATTLES);

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("Select Your Kit");

        List<Kit> allKits = kitManager.getAllKits();

        for (Kit kit : allKits) {
            String buttonText = kit.getName();
            boolean unlocked = kitManager.isKitUnlocked(statsService, playerId, kit);

            if (unlocked) {
                buttonText += " (Selected)"; // Ou juste le nom si déjà sélectionné
            } else if (kit.isDefaultUnlocked()) {
                buttonText += " (Default)";
            } else {
                buttonText += " (Locked - " + kit.getPrice() + " coins";
                if (kit.getRequiredLevel() > 0) {
                    buttonText += ", Lvl " + kit.getRequiredLevel();
                }
                buttonText += ")";
            }
            // Pour l'image, il faudrait une URL ou un chemin d'accès à une image
            // représentative du kit.
            // formBuilder.button(buttonText, FormImage.Type.URL, "URL_DE_L_IMAGE_DU_KIT");
            formBuilder.button(buttonText);
        }

        formBuilder.closedOrInvalidResultHandler(() -> {
            // Le joueur a fermé le formulaire sans choisir
            player.sendMessage(ChatColor.YELLOW + "Kit selection cancelled.");
        });

        formBuilder.validResultHandler(response -> {
            Kit selectedKit = allKits.get(response.clickedButtonId());

            if (kitManager.isKitUnlocked(statsService, playerId, selectedKit)) {
                player.sendMessage(ChatColor.GREEN + "You selected kit: " + selectedKit.getName());
                // kitManager.selectKit(statsService, playerId, selectedKit);
                // Équiper ou marquer pour la prochaine partie
            } else {
                // Tenter l'achat
                if (kitManager.hasRequiredLevelForKit(statsService, playerId, selectedKit)
                        && kitManager.canAffordKit(statsService, playerId, selectedKit)) {
                    KitManager.PurchaseResult result = kitManager.purchaseKit(statsService, playerId, selectedKit);
                    if (result == KitManager.PurchaseResult.SUCCESS) {
                        player.sendMessage(
                                ChatColor.GREEN + "Kit " + selectedKit.getName() + " purchased and selected!");
                        // playerDataService.savePlayerData(playerDataResponse); // Sauvegarder
                        // kitManager.selectKit(playerDataResponse, selectedKit);
                        openKitSelectionForm(player); // Refresh form
                    } else {
                        player.sendMessage(ChatColor.RED + "Could not purchase kit. Reason: " + result.toString());
                        openKitSelectionForm(player); // Refresh form
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You cannot purchase this kit (level or coins).");
                    openKitSelectionForm(player); // Refresh form
                }
            }
        });
        GeyserApi.api().sendForm(player.getUniqueId(), formBuilder.build()); // Ajout de .build()
    }

    // Nouvelle méthode pour le formulaire de confirmation/détail (Bedrock)
    private void openKitConfirmationForm(Player player, MinigameStats playerStats, Kit kit) {
        ModalForm.Builder confirmationForm = ModalForm.builder()
                .title(kit.getName());

        String contentText = kit.getDescription() + "\n\n";
        UUID playerId = player.getUniqueId();
        boolean unlocked = kitManager.isKitUnlocked(statsService, playerId, kit);
        boolean canAfford = kitManager.canAffordKit(statsService, playerId, kit);
        boolean hasLevel = kitManager.hasRequiredLevelForKit(statsService, playerId, kit);

        if (unlocked) {
            contentText += ChatColor.GREEN + "You own this kit.\n";
            confirmationForm.button1(ChatColor.GREEN + "Select Kit"); // Bouton 1: Sélectionner
            confirmationForm.button2(ChatColor.GRAY + "Back"); // Bouton 2: Retour
        } else if (kit.isDefaultUnlocked()) {
            contentText += ChatColor.YELLOW + "This is a default kit.\n";
            confirmationForm.button1(ChatColor.GREEN + "Select Kit");
            confirmationForm.button2(ChatColor.GRAY + "Back");
        } else {
            contentText += ChatColor.GOLD + "Price: " + kit.getPrice() + " coins\n";
            if (kit.getRequiredLevel() > 0) {
                contentText += ChatColor.AQUA + "Required Level: " + kit.getRequiredLevel() + "\n";
            }
            if (!hasLevel) {
                contentText += ChatColor.RED + "You need level " + kit.getRequiredLevel() + ".\n";
                confirmationForm.button1(ChatColor.GRAY + "Purchase (Unavailable)");
            } else if (!canAfford) {
                contentText += ChatColor.RED + "You need " + kit.getPrice() + " coins.\n";
                confirmationForm.button1(ChatColor.GRAY + "Purchase (Unavailable)");
            } else {
                contentText += ChatColor.YELLOW + "Do you want to purchase this kit?\n";
                confirmationForm.button1(ChatColor.GREEN + "Purchase Kit");
            }
            confirmationForm.button2(ChatColor.GRAY + "Back");
        }

        confirmationForm.content(contentText);

        confirmationForm.validResultHandler(response -> {
            if (response.clickedButtonId() == 0) { // Bouton 1 cliqué
                if (unlocked || kit.isDefaultUnlocked()) {
                    player.sendMessage(ChatColor.GREEN + "You selected kit: " + kit.getName());
                    // kitManager.selectKit(playerData, kit);
                } else if (hasLevel && canAfford) {
                    KitManager.PurchaseResult result = kitManager.purchaseKit(statsService, playerId, kit);
                    if (result == KitManager.PurchaseResult.SUCCESS) {
                        player.sendMessage(ChatColor.GREEN + "Kit " + kit.getName() + " purchased and selected!");
                        // playerDataService.savePlayerData(playerData);
                        // kitManager.selectKit(playerData, kit);
                        openKitSelectionForm(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "Could not purchase kit. Reason: " + result.toString());
                        openKitSelectionForm(player);
                    }
                } else {
                    openKitSelectionForm(player);
                }
            } else {
                openKitSelectionForm(player);
            }
        });
        confirmationForm.closedOrInvalidResultHandler(() -> openKitSelectionForm(player));

        GeyserApi.api().sendForm(player.getUniqueId(), confirmationForm.build());
    }

    // Méthode MOCK pour simuler la récupération de PlayerData
    // À REMPLACER par la vraie logique de chargement/gestion de PlayerData
    private PlayerData getMockPlayerData(UUID uuid, String name) {
        PlayerData pd = new PlayerData();
        pd.setId(uuid);
        pd.setName(name);
        pd.setCoins(500); // Exemple de pièces
        pd.setLevel(10); // Exemple de niveau
        // Simuler quelques kits débloqués
        // pd.unlockKit("Explosive Archer");
        return pd;
    }
}