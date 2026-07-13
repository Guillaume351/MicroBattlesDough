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

import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitLevel;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.kits.TieredKit;
import com.cookiebuild.microbattles.ui.KitDisplayInfo;

public class BedrockKitSelectionUI {

    private final KitManager kitManager;
    private final MinigameProgressionService minigameStatsService;

    public BedrockKitSelectionUI(KitManager kitManager, MinigameProgressionService minigameStatsService) {
        this.kitManager = kitManager;
        // Core operations are transaction-scoped; never retain a caller-owned persistence context.
        this.minigameStatsService = new MinigameProgressionService(null);
    }

    public void open(Player player) {
        // --- Get Player Stats ---
        UUID playerId = player.getUniqueId();
        int playerLevel = minigameStatsService.getLevel(playerId, MinigameProgressionService.MICROBATTLES);
        int playerCoins = minigameStatsService.getCoins(playerId, MinigameProgressionService.MICROBATTLES);
        com.cookiebuild.cookiedough.model.MinigameProgression progressionStats = minigameStatsService
                .getOrCreateStats(playerId, MinigameProgressionService.MICROBATTLES);
        int playerXp = progressionStats.getExperience();
        int xpForNextLevel = progressionStats.getExperienceForNextLevel();
        String statsContent = String.format("§9§lLevel: §f§l%d\n§6§lCoins: §f§l%d\n§a§lXP: §f§l%d/%d\n\n§6Weekly free: §b%s", playerLevel,
                playerCoins,
                playerXp, xpForNextLevel, String.join(" §7/ §b", kitManager.getWeeklyFreeKits()));

        // --- Get and Filter Kits ---
        List<KitDisplayInfo> allKits = getKitDisplayInfos(player);
        List<KitDisplayInfo> ownedKits = allKits.stream().filter(k -> k.unlocked).collect(Collectors.toList());

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§l§9Kit Selection")
                .content(statsContent);

        // Add owned kits to main menu
        if (!ownedKits.isEmpty()) {
            formBuilder.button("§a§l--- Your Kits ---");
            ownedKits.forEach(kit -> formBuilder.button(createOwnedKitButtonText(kit)));
        }

        // Add shop button
        formBuilder.button("§e§l🏪 Kit Shop");

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
        int playerLevel = minigameStatsService.getLevel(playerId, MinigameProgressionService.MICROBATTLES);
        int playerCoins = minigameStatsService.getCoins(playerId, MinigameProgressionService.MICROBATTLES);
        com.cookiebuild.cookiedough.model.MinigameProgression progressionStats = minigameStatsService
                .getOrCreateStats(playerId, MinigameProgressionService.MICROBATTLES);
        int playerXp = progressionStats.getExperience();
        int xpForNextLevel = progressionStats.getExperienceForNextLevel();
        String statsContent = String.format("§9§lLevel: §f§l%d  §6§lCoins: §f§l%d\n§a§lXP: §f§l%d/%d", playerLevel, playerCoins, playerXp, xpForNextLevel);

        // --- Get, Filter, and Sort Kits ---
        List<KitDisplayInfo> allKits = getKitDisplayInfos(player);
        List<KitDisplayInfo> availableKits = allKits.stream().filter(k -> !k.unlocked && k.hasLevel && k.canAfford)
                .collect(Collectors.toList());
        List<KitDisplayInfo> lockedKits = allKits.stream().filter(k -> !k.unlocked && (!k.hasLevel || !k.canAfford))
                .collect(Collectors.toList());

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§l§6Kit Shop")
                .content(statsContent);

        // Create buttons list for handling clicks
        List<KitDisplayInfo> buttonMapping = new ArrayList<>();

        // Add back button at the top for easy access
        formBuilder.button("§f§l⬅ §9§lBack to Kit Selection");
        buttonMapping.add(null); // Back button

        // Add available kits
        if (!availableKits.isEmpty()) {
            formBuilder.button("§e§l--- Available for Purchase ---");
            buttonMapping.add(null); // Separator
            availableKits.forEach(kit -> {
                formBuilder.button(createShopKitButtonText(kit));
                buttonMapping.add(kit);
            });
        }

        // Add locked kits
        if (!lockedKits.isEmpty()) {
            formBuilder.button("§c§l--- Locked Kits ---");
            buttonMapping.add(null); // Separator
            lockedKits.forEach(kit -> {
                formBuilder.button(createShopKitButtonText(kit));
                buttonMapping.add(kit);
            });
        }

        formBuilder.validResultHandler(response -> {
            int buttonId = response.getClickedButtonId();

            if (buttonId == 0) {
                // Back button clicked (now at top)
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
        String rotation = kit.level == 1 && kitManager.isWeeklyFreeKit(kit.kitName) ? " §6★" : "";
        return "§a§l✓ §r§f§l" + displayName + rotation;
    }

    private String createShopKitButtonText(KitDisplayInfo kit) {
        String displayName = kit.level == 0 ? kit.kitName : kit.kitLevel.getDisplayName(kit.kitName);
        if (kit.hasLevel && kit.canAfford) {
            return String.format("§e§l%s\n§f§lPrice: §6§l%d coins", displayName, kit.kitLevel.getPrice());
        } else {
            String requirement = !kit.hasLevel ? "Requires Level " + kit.kitLevel.getRequiredLevel()
                    : "Not enough coins";
            return String.format("§c§l%s\n§8§l%s", displayName, requirement);
        }
    }

    private void handleKitSelection(Player player, KitDisplayInfo kitInfo) {
        String displayName = kitInfo.level == 0 ? kitInfo.kitName
                : kitInfo.kitLevel.getDisplayName(kitInfo.kitName);
        if (kitInfo.level == 0) {
            kitManager.selectKit(player.getUniqueId(), kitInfo.kitName, kitInfo.level);
            player.sendMessage(ChatColor.GREEN + "Selected kit: " + displayName);
            return;
        }
        ModalForm form = ModalForm.builder()
                .title("§l§9" + displayName)
                .content(kitManager.getKitDescription(kitInfo.kitName, player)
                        + "\n\nSelect it, or practice-preview it for 8 seconds before the match.")
                .button1("§a§lSelect")
                .button2("§b§lPreview")
                .validResultHandler(response -> {
                    if (response.getClickedButtonId() == 0) {
                        kitManager.selectKit(player.getUniqueId(), kitInfo.kitName, kitInfo.level);
                        player.sendMessage(ChatColor.GREEN + "Selected kit: " + displayName);
                    } else if (!kitManager.previewKit(player, kitInfo.kitName, kitInfo.level)) {
                        player.sendMessage(ChatColor.RED + "Preview is only available before the match starts.");
                    }
                }).build();
        sendForm(player, form);
    }

    private void handleShopKitSelection(Player player, KitDisplayInfo kitInfo) {
        openPurchaseConfirmationForm(player, kitInfo);
    }

    private void openPurchaseConfirmationForm(Player player, KitDisplayInfo kitInfo) {
        boolean purchasable = kitInfo.hasLevel && kitInfo.canAfford;
        List<String> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§l§e" + kitInfo.kitLevel.getDisplayName(kitInfo.kitName))
                .content(kitManager.getKitDescription(kitInfo.kitName, player) + "\n\n§6Price: §f"
                        + kitInfo.kitLevel.getPrice() + " coins"
                        + (purchasable ? "" : "\n§cPurchase requirements are not met."));
        if (purchasable) {
            builder.button("§a§lPurchase and Select");
            actions.add("purchase");
        }
        builder.button("§b§lPractice Preview (8s)");
        actions.add("preview");
        builder.button("§f§lBack");
        actions.add("back");
        builder.validResultHandler(response -> {
                    String action = actions.get(response.getClickedButtonId());
                    if (action.equals("purchase")) {
                        boolean success = kitManager.purchaseKitLevel(minigameStatsService, player.getUniqueId(),
                                kitInfo.kitName, kitInfo.level);
                        if (success) {
                            kitManager.selectKit(player.getUniqueId(), kitInfo.kitName, kitInfo.level);
                            player.sendMessage(ChatColor.GREEN + "Purchased and selected: "
                                    + kitInfo.kitLevel.getDisplayName(kitInfo.kitName));
                        } else {
                            player.sendMessage(ChatColor.RED + "Purchase failed. Check your coins and level.");
                        }
                        openShopMenu(player);
                    } else if (action.equals("preview")) {
                        if (!kitManager.previewKit(player, kitInfo.kitName, kitInfo.level)) {
                            player.sendMessage(ChatColor.RED + "Preview is only available before the match starts.");
                        }
                    } else {
                        openShopMenu(player);
                    }
                });
        sendForm(player, builder);
    }

    private List<KitDisplayInfo> getKitDisplayInfos(Player player) {
        UUID playerId = player.getUniqueId();
        var progression = minigameStatsService.getOrCreateStats(playerId,
                MinigameProgressionService.MICROBATTLES);
        int coins = minigameStatsService.getCoins(playerId, MinigameProgressionService.MICROBATTLES);
        List<KitDisplayInfo> kits = new ArrayList<>();
        Kit defaultKit = kitManager.getOriginalKit("Default");

        if (defaultKit != null) {
            kits.add(new KitDisplayInfo(defaultKit.getName(), 0, true, true, true, defaultKit, player));
        }

        for (TieredKit tieredKit : kitManager.getAllTieredKits()) {
            for (KitLevel level : tieredKit.getLevels()) {
                // *** FIX: Only show level if previous level is unlocked ***
                boolean weeklyTierOne = level.getLevel() == 1
                        && kitManager.isWeeklyFreeKit(tieredKit.getBaseName());
                boolean previousLevelUnlocked = level.getLevel() == 1
                        || progression.hasUnlockedKit(tieredKit.getBaseName() + ":L" + (level.getLevel() - 1))
                        || (level.getLevel() == 2 && kitManager.isWeeklyFreeKit(tieredKit.getBaseName()));

                if (!previousLevelUnlocked) {
                    continue;
                }

                boolean isUnlocked = level.isDefaultUnlocked() || weeklyTierOne
                        || progression.hasUnlockedKit(tieredKit.getBaseName() + ":L" + level.getLevel());
                boolean hasLevel = progression.getLevel() >= level.getRequiredLevel();
                boolean canAfford = coins >= level.getPrice();
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
