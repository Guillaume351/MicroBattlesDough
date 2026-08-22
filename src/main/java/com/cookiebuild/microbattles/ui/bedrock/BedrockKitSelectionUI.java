package com.cookiebuild.microbattles.ui.bedrock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitLevel;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.kits.TieredKit;
import com.cookiebuild.microbattles.ui.KitDisplayInfo;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;

public class BedrockKitSelectionUI {

    private final KitManager kitManager;
    private final MinigameProgressionService minigameStatsService;
    private final Set<UUID> menuLoads = ConcurrentHashMap.newKeySet();

    public BedrockKitSelectionUI(KitManager kitManager, MinigameProgressionService minigameStatsService) {
        this.kitManager = kitManager;
        // Core operations are transaction-scoped; never retain a caller-owned persistence context.
        this.minigameStatsService = new MinigameProgressionService(null);
    }

    public void open(Player player) {
        UUID playerId = player.getUniqueId();
        if (!menuLoads.add(playerId)) {
            player.sendMessage(ChatColor.YELLOW + message(player, "microbattles.kit.menu.loading"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(MicroBattles.getInstance(), () -> {
            try {
                renderMainMenu(player);
            } catch (RuntimeException error) {
                MicroBattles.getInstance().getLogger().warning(
                        "Could not load Bedrock kit menu for " + playerId + ": " + error.getMessage());
                Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () ->
                        player.sendMessage(ChatColor.RED + message(player, "microbattles.kit.menu.failed")));
            } finally {
                menuLoads.remove(playerId);
            }
        });
    }

    private void renderMainMenu(Player player) {
        // --- Get Player Stats ---
        UUID playerId = player.getUniqueId();
        int playerLevel = minigameStatsService.getLevel(playerId, MinigameProgressionService.MICROBATTLES);
        int playerCoins = minigameStatsService.getCoins(playerId, MinigameProgressionService.MICROBATTLES);
        com.cookiebuild.cookiedough.model.MinigameProgression progressionStats = minigameStatsService
                .getOrCreateStats(playerId, MinigameProgressionService.MICROBATTLES);
        int playerXp = progressionStats.getExperience();
        int xpForNextLevel = progressionStats.getExperienceForNextLevel();
        String statsContent = message(player, "microbattles.kit.bedrock.stats", playerLevel, playerCoins,
                playerXp, xpForNextLevel, String.join(" §7/ §b", kitManager.getWeeklyFreeKits()));

        // --- Get and Filter Kits ---
        List<KitDisplayInfo> allKits = getKitDisplayInfos(player);
        List<KitDisplayInfo> ownedKits = allKits.stream().filter(k -> k.unlocked).collect(Collectors.toList());

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§l§9" + message(player, "microbattles.kit.menu.title"))
                .content(statsContent);

        // Each kit is an actionable row; headings live in content instead of becoming fake buttons.
        List<KitDisplayInfo> buttonMapping = new ArrayList<>();
        ownedKits.forEach(kit -> {
            BedrockFormImages.button(formBuilder, createOwnedKitButtonText(kit), imageId(kit.kitName));
            buttonMapping.add(kit);
        });
        int shopIndex = buttonMapping.size();
        BedrockFormImages.button(formBuilder, "§e§l🏪 " + message(player, "microbattles.kit.shop.title"),
                "actions/shop");
        buttonMapping.add(null);
        int closeIndex = buttonMapping.size();
        BedrockFormImages.button(formBuilder, "§c§l" + message(player, "microbattles.kit.close"),
                "actions/close");
        buttonMapping.add(null);

        formBuilder.validResultHandler(response -> {
            int buttonId = response.getClickedButtonId();

            if (buttonId == shopIndex) {
                openShopMenu(player);
            } else if (buttonId != closeIndex && buttonId >= 0 && buttonId < buttonMapping.size()) {
                KitDisplayInfo selectedKit = buttonMapping.get(buttonId);
                if (selectedKit != null) {
                    handleKitSelection(player, selectedKit);
                }
            }
        });

        sendForm(player, formBuilder);
    }

    private void openShopMenu(Player player) {
        UUID playerId = player.getUniqueId();
        if (!menuLoads.add(playerId)) {
            player.sendMessage(ChatColor.YELLOW + message(player, "microbattles.kit.menu.loading"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(MicroBattles.getInstance(), () -> {
            try {
                renderShopMenu(player);
            } catch (RuntimeException error) {
                MicroBattles.getInstance().getLogger().warning(
                        "Could not load Bedrock kit shop for " + playerId + ": " + error.getMessage());
                Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () ->
                        player.sendMessage(ChatColor.RED + message(player, "microbattles.kit.menu.failed")));
            } finally {
                menuLoads.remove(playerId);
            }
        });
    }

    private void renderShopMenu(Player player) {
        // --- Get Player Stats ---
        UUID playerId = player.getUniqueId();
        int playerLevel = minigameStatsService.getLevel(playerId, MinigameProgressionService.MICROBATTLES);
        int playerCoins = minigameStatsService.getCoins(playerId, MinigameProgressionService.MICROBATTLES);
        com.cookiebuild.cookiedough.model.MinigameProgression progressionStats = minigameStatsService
                .getOrCreateStats(playerId, MinigameProgressionService.MICROBATTLES);
        int playerXp = progressionStats.getExperience();
        int xpForNextLevel = progressionStats.getExperienceForNextLevel();
        String statsContent = message(player, "microbattles.kit.bedrock.shop_stats",
                playerLevel, playerCoins, playerXp, xpForNextLevel);

        // --- Get, Filter, and Sort Kits ---
        List<KitDisplayInfo> allKits = getKitDisplayInfos(player);
        List<KitDisplayInfo> availableKits = allKits.stream().filter(k -> !k.unlocked && k.hasLevel && k.canAfford)
                .collect(Collectors.toList());
        List<KitDisplayInfo> lockedKits = allKits.stream().filter(k -> !k.unlocked && (!k.hasLevel || !k.canAfford))
                .collect(Collectors.toList());

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§l§6" + message(player, "microbattles.kit.shop.title"))
                .content(statsContent);

        // Create buttons list for handling clicks
        List<KitDisplayInfo> buttonMapping = new ArrayList<>();

        // Add back button at the top for easy access
        BedrockFormImages.button(formBuilder, "§f§l⬅ §9§l" + message(player, "microbattles.kit.shop.back"),
                "actions/back");
        buttonMapping.add(null); // Back button

        availableKits.forEach(kit -> {
            BedrockFormImages.button(formBuilder, createShopKitButtonText(kit), imageId(kit.kitName));
            buttonMapping.add(kit);
        });
        lockedKits.forEach(kit -> {
            BedrockFormImages.button(formBuilder, createShopKitButtonText(kit), imageId(kit.kitName));
            buttonMapping.add(kit);
        });
        int closeIndex = buttonMapping.size();
        BedrockFormImages.button(formBuilder, "§c§l" + message(player, "microbattles.kit.close"),
                "actions/close");
        buttonMapping.add(null);

        formBuilder.validResultHandler(response -> {
            int buttonId = response.getClickedButtonId();

            if (buttonId == 0) {
                // Back button clicked (now at top)
                open(player);
            } else if (buttonId != closeIndex && buttonId >= 0 && buttonId < buttonMapping.size()) {
                KitDisplayInfo selectedKit = buttonMapping.get(buttonId);
                if (selectedKit != null) {
                    handleShopKitSelection(player, selectedKit);
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
            return "§e§l" + displayName + "\n§f§l"
                    + message(kit.player, "microbattles.kit.price", kit.kitLevel.getPrice());
        } else {
            String requirement = !kit.hasLevel
                    ? message(kit.player, "microbattles.kit.requires_level", kit.kitLevel.getRequiredLevel())
                    : message(kit.player, "microbattles.kit.not_enough_coins");
            return "§c§l" + displayName + "\n§8§l" + requirement;
        }
    }

    private void handleKitSelection(Player player, KitDisplayInfo kitInfo) {
        String displayName = kitInfo.level == 0 ? kitInfo.kitName
                : kitInfo.kitLevel.getDisplayName(kitInfo.kitName);
        if (kitInfo.level == 0) {
            mutateKit(player, kitInfo, false, false);
            return;
        }
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§9" + displayName)
                .content(kitManager.getKitDescription(kitInfo.kitName, player)
                        + "\n\n" + message(player, "microbattles.kit.preview.detail"))
                .validResultHandler(response -> {
                    if (response.getClickedButtonId() == 0) {
                        mutateKit(player, kitInfo, false, false);
                    } else if (response.getClickedButtonId() == 1) {
                        Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
                            if (!kitManager.previewKit(player, kitInfo.kitName, kitInfo.level)) {
                                player.sendMessage(ChatColor.RED + message(
                                        player, "microbattles.kit.preview.unavailable"));
                            }
                        });
                    } else {
                        open(player);
                    }
                });
        BedrockFormImages.button(form, "§a§l" + message(player, "microbattles.kit.select.button"),
                imageId(kitInfo.kitName));
        BedrockFormImages.button(form, "§b§l" + message(player, "microbattles.kit.preview.button"),
                "actions/preview");
        BedrockFormImages.button(form, "§f§l" + message(player, "microbattles.kit.back"), "actions/back");
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
                .content(kitManager.getKitDescription(kitInfo.kitName, player) + "\n\n§6"
                        + message(player, "microbattles.kit.price", kitInfo.kitLevel.getPrice())
                        + (purchasable ? "" : "\n§c" + message(
                                player, "microbattles.kit.requirements_not_met")));
        if (purchasable) {
            BedrockFormImages.button(builder, "§a§l" + message(player, "microbattles.kit.purchase_select"),
                    "actions/purchase");
            actions.add("purchase");
        }
        BedrockFormImages.button(builder, "§b§l" + message(player, "microbattles.kit.preview.button"),
                "actions/preview");
        actions.add("preview");
        BedrockFormImages.button(builder, "§f§l" + message(player, "microbattles.kit.back"), "actions/back");
        actions.add("back");
        builder.validResultHandler(response -> {
                    String action = actions.get(response.getClickedButtonId());
                    if (action.equals("purchase")) {
                        mutateKit(player, kitInfo, true, true);
                    } else if (action.equals("preview")) {
                        Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
                            if (!kitManager.previewKit(player, kitInfo.kitName, kitInfo.level)) {
                                player.sendMessage(ChatColor.RED + message(
                                        player, "microbattles.kit.preview.unavailable"));
                            }
                        });
                    } else {
                        openShopMenu(player);
                    }
                });
        sendForm(player, builder);
    }

    private void mutateKit(Player player, KitDisplayInfo kitInfo, boolean purchase, boolean reopenShop) {
        UUID playerId = player.getUniqueId();
        if (!kitManager.tryBeginMutation(playerId)) {
            player.sendMessage(ChatColor.YELLOW + message(player, "microbattles.kit.action.busy"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(MicroBattles.getInstance(), () -> {
            boolean success = false;
            try {
                success = !purchase || kitManager.purchaseKitLevel(
                        minigameStatsService, playerId, kitInfo.kitName, kitInfo.level);
                if (success) kitManager.selectKit(playerId, kitInfo.kitName, kitInfo.level);
            } catch (RuntimeException error) {
                MicroBattles.getInstance().getLogger().warning(
                        "Could not update Bedrock kit for " + playerId + ": " + error.getMessage());
                success = false;
            } finally {
                kitManager.finishMutation(playerId);
            }
            boolean completed = success;
            Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
                if (!player.isOnline()) return;
                String displayName = kitInfo.level == 0 ? kitInfo.kitName
                        : kitInfo.kitLevel.getDisplayName(kitInfo.kitName);
                String key = completed
                        ? purchase ? "microbattles.kit.action.purchased" : "microbattles.kit.action.selected"
                        : "microbattles.kit.action.failed";
                player.sendMessage((completed ? ChatColor.GREEN : ChatColor.RED)
                        + message(player, key, displayName));
                if (reopenShop) openShopMenu(player);
            });
        });
    }

    private static String message(Player player, String key, Object... arguments) {
        return LocaleManager.getMessage(key, player.locale(), arguments);
    }

    static String imageId(String kitName) {
        return switch (kitName) {
            case "Default" -> "kits/microbattles/default";
            case "Explosive Archer" -> "kits/microbattles/explosive_archer";
            case "Enderman" -> "kits/microbattles/enderman";
            case "Knockback Warrior" -> "kits/microbattles/knockback_warrior";
            case "Tank" -> "kits/microbattles/tank";
            case "Ninja" -> "kits/microbattles/ninja";
            case "Archer" -> "kits/microbattles/archer";
            case "Berserker" -> "kits/microbattles/berserker";
            case "Chemist" -> "kits/microbattles/chemist";
            case "Assassin" -> "kits/microbattles/assassin";
            case "Miner" -> "kits/microbattles/miner";
            case "Vampire" -> "kits/microbattles/vampire";
            case "Frost Mage" -> "kits/microbattles/frost_mage";
            case "Juggernaut" -> "kits/microbattles/juggernaut";
            case "Trapper" -> "kits/microbattles/trapper";
            case "Alchemist" -> "kits/microbattles/alchemist";
            case "Mobility" -> "kits/microbattles/mobility";
            default -> "modes/microbattles";
        };
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

}
