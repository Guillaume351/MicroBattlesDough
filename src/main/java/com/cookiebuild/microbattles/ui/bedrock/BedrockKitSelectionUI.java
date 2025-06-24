package com.cookiebuild.microbattles.ui.bedrock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitLevel;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.kits.TieredKit;
import com.cookiebuild.microbattles.ui.KitDisplayInfo;

public class BedrockKitSelectionUI {

    private final KitManager kitManager;
    private final MinigameStatsService minigameStatsService;

    public BedrockKitSelectionUI(KitManager kitManager, MinigameStatsService minigameStatsService) {
        this.kitManager = kitManager;
        this.minigameStatsService = minigameStatsService;
    }

    public void open(Player player) {
        // --- Get Player Stats ---
        UUID playerId = player.getUniqueId();
        int playerLevel = minigameStatsService.getLevel(playerId, MinigameStatsService.MICROBATTLES);
        int playerCoins = minigameStatsService.getCoins(playerId, MinigameStatsService.MICROBATTLES);
        int playerXp = minigameStatsService.getOrCreateStats(playerId, MinigameStatsService.MICROBATTLES)
                .getExperience();
        String statsContent = String.format("§eLevel: §f%d\n§6Coins: §f%d\n§aXP: §f%d", playerLevel, playerCoins,
                playerXp);

        // --- Get and Filter Kits ---
        List<KitDisplayInfo> allKits = getKitDisplayInfos(player);
        List<KitDisplayInfo> ownedKits = allKits.stream().filter(k -> k.unlocked).collect(Collectors.toList());

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("Kit Selection")
                .content(statsContent);

        // Add owned kits to main menu
        if (!ownedKits.isEmpty()) {
            formBuilder.button("§a--- Your Kits ---");
            ownedKits.forEach(kit -> formBuilder.button(createOwnedKitButtonText(kit)));
        }

        // Add shop button
        formBuilder.button("§e🏪 Kit Shop");

        // Create buttons list for handling clicks
        List<KitDisplayInfo> buttonMapping = new ArrayList<>();
        buttonMapping.add(null); // Separator "Your Kits"
        buttonMapping.addAll(ownedKits);
        buttonMapping.add(null); // Shop button

        formBuilder.validResultHandler(response -> {
            int buttonId = response.getClickedButtonId();

            if (buttonId == buttonMapping.size() - 1) {
                // Shop button clicked
                openShopMenu(player);
            } else if (buttonId > 0 && buttonId < buttonMapping.size() - 1) {
                // Kit selected
                KitDisplayInfo selectedKit = buttonMapping.get(buttonId);
                if (selectedKit != null) {
                    handleKitSelection(player, selectedKit);
                }
            } else {
                // Separator clicked, reopen menu
                open(player);
            }
        });

        sendForm(player, formBuilder);
    }

    private void openShopMenu(Player player) {
        // --- Get Player Stats ---
        UUID playerId = player.getUniqueId();
        int playerLevel = minigameStatsService.getLevel(playerId, MinigameStatsService.MICROBATTLES);
        int playerCoins = minigameStatsService.getCoins(playerId, MinigameStatsService.MICROBATTLES);
        String statsContent = String.format("§eLevel: §f%d  §6Coins: §f%d", playerLevel, playerCoins);

        // --- Get, Filter, and Sort Kits ---
        List<KitDisplayInfo> allKits = getKitDisplayInfos(player);
        List<KitDisplayInfo> availableKits = allKits.stream().filter(k -> !k.unlocked && k.hasLevel && k.canAfford)
                .collect(Collectors.toList());
        List<KitDisplayInfo> lockedKits = allKits.stream().filter(k -> !k.unlocked && (!k.hasLevel || !k.canAfford))
                .collect(Collectors.toList());

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("Kit Shop")
                .content(statsContent);

        // Create buttons list for handling clicks
        List<KitDisplayInfo> buttonMapping = new ArrayList<>();

        // Add available kits
        if (!availableKits.isEmpty()) {
            formBuilder.button("§e--- Available for Purchase ---");
            buttonMapping.add(null); // Separator
            availableKits.forEach(kit -> {
                formBuilder.button(createShopKitButtonText(kit));
                buttonMapping.add(kit);
            });
        }

        // Add locked kits
        if (!lockedKits.isEmpty()) {
            formBuilder.button("§c--- Locked Kits ---");
            buttonMapping.add(null); // Separator
            lockedKits.forEach(kit -> {
                formBuilder.button(createShopKitButtonText(kit));
                buttonMapping.add(kit);
            });
        }

        // Add back button
        formBuilder.button("§7← Back to Kit Selection");
        buttonMapping.add(null); // Back button

        formBuilder.validResultHandler(response -> {
            int buttonId = response.getClickedButtonId();

            if (buttonId == buttonMapping.size() - 1) {
                // Back button clicked
                open(player);
            } else {
                KitDisplayInfo selectedKit = buttonMapping.get(buttonId);
                if (selectedKit != null) {
                    handleShopKitSelection(player, selectedKit);
                } else {
                    // Separator clicked, reopen shop
                    openShopMenu(player);
                }
            }
        });

        sendForm(player, formBuilder);
    }

    private String createOwnedKitButtonText(KitDisplayInfo kit) {
        String displayName = kit.level == 0 ? kit.kitName : kit.kitLevel.getDisplayName(kit.kitName);
        return "§a✓ " + displayName;
    }

    private String createShopKitButtonText(KitDisplayInfo kit) {
        String displayName = kit.level == 0 ? kit.kitName : kit.kitLevel.getDisplayName(kit.kitName);
        if (kit.hasLevel && kit.canAfford) {
            return String.format("§e%s\n§fPrice: §6%d coins", displayName, kit.kitLevel.getPrice());
        } else {
            String requirement = !kit.hasLevel ? "Requires Level " + kit.kitLevel.getRequiredLevel()
                    : "Not enough coins";
            return String.format("§c%s\n§8%s", displayName, requirement);
        }
    }

    private void handleKitSelection(Player player, KitDisplayInfo kitInfo) {
        // For owned kits - just select them
        kitManager.selectKit(player.getUniqueId(), kitInfo.kitName, kitInfo.level);
        player.sendMessage(ChatColor.GREEN + "Selected kit: " +
                (kitInfo.level == 0 ? kitInfo.kitName : kitInfo.kitLevel.getDisplayName(kitInfo.kitName)));
        player.closeInventory(); // Close the form
    }

    private void handleShopKitSelection(Player player, KitDisplayInfo kitInfo) {
        if (kitInfo.hasLevel && kitInfo.canAfford) {
            openPurchaseConfirmationForm(player, kitInfo);
        } else {
            String reason = !kitInfo.hasLevel
                    ? "You need to reach level " + kitInfo.kitLevel.getRequiredLevel() + " first!"
                    : "You don't have enough coins!";
            player.sendMessage(ChatColor.RED + reason);
            openShopMenu(player);
        }
    }

    private void openPurchaseConfirmationForm(Player player, KitDisplayInfo kitInfo) {
        String title = "Confirm Purchase";
        String content = String.format("Purchase §e%s §rfor §6%d coins§r?",
                kitInfo.kitLevel.getDisplayName(kitInfo.kitName), kitInfo.kitLevel.getPrice());

        ModalForm form = ModalForm.builder()
                .title(title)
                .content(content)
                .button1("§aConfirm")
                .button2("§cCancel")
                .validResultHandler(response -> {
                    if (response.getClickedButtonId() == 0) {
                        boolean success = kitManager.purchaseKitLevel(minigameStatsService, player.getUniqueId(),
                                kitInfo.kitName, kitInfo.level);
                        if (success) {
                            player.sendMessage(ChatColor.GREEN + "Purchase successful!");
                            // Auto-select the newly purchased kit
                            kitManager.selectKit(player.getUniqueId(), kitInfo.kitName, kitInfo.level);
                            player.sendMessage(ChatColor.GREEN + "Kit equipped: "
                                    + kitInfo.kitLevel.getDisplayName(kitInfo.kitName));
                        } else {
                            player.sendMessage(ChatColor.RED + "Purchase failed. Please try again.");
                        }
                    }
                    openShopMenu(player); // Return to shop
                })
                .build();
        sendForm(player, form);
    }

    private List<KitDisplayInfo> getKitDisplayInfos(Player player) {
        UUID playerId = player.getUniqueId();
        List<KitDisplayInfo> kits = new ArrayList<>();
        Kit defaultKit = kitManager.getOriginalKit("Default");

        if (defaultKit != null) {
            kits.add(new KitDisplayInfo(defaultKit.getName(), 0, true, true, true, defaultKit, player));
        }

        for (TieredKit tieredKit : kitManager.getAllTieredKits()) {
            for (KitLevel level : tieredKit.getLevels()) {
                // *** FIX: Only show level if previous level is unlocked ***
                boolean previousLevelUnlocked = level.getLevel() == 1
                        || kitManager.isKitLevelUnlocked(minigameStatsService, playerId, tieredKit.getBaseName(),
                                level.getLevel() - 1);

                if (!previousLevelUnlocked) {
                    continue;
                }

                boolean isUnlocked = kitManager.isKitLevelUnlocked(minigameStatsService, playerId,
                        tieredKit.getBaseName(), level.getLevel());
                boolean hasLevel = kitManager.hasRequiredPlayerLevelForKit(minigameStatsService, playerId,
                        tieredKit.getBaseName(), level.getLevel());
                boolean canAfford = kitManager.canAffordKitLevel(minigameStatsService, playerId,
                        tieredKit.getBaseName(), level.getLevel());
                kits.add(new KitDisplayInfo(tieredKit.getBaseName(), level.getLevel(), isUnlocked, hasLevel, canAfford,
                        tieredKit, level, player));
            }
        }

        // --- SORTING ---
        kits.sort(Comparator.comparing((KitDisplayInfo k) -> k.kitName).thenComparingInt(k -> k.level));
        return kits;
    }

    private void sendForm(Player player, SimpleForm.Builder form) {
        FloodgatePlayer fgPlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (fgPlayer != null)
            fgPlayer.sendForm(form.build());
    }

    private void sendForm(Player player, CustomForm form) {
        FloodgatePlayer fgPlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (fgPlayer != null)
            fgPlayer.sendForm(form);
    }

    private void sendForm(Player player, ModalForm form) {
        FloodgatePlayer fgPlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (fgPlayer != null)
            fgPlayer.sendForm(form);
    }
}
