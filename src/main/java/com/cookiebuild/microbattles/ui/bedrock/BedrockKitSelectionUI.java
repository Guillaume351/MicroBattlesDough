package com.cookiebuild.microbattles.ui.bedrock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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
import com.cookiebuild.microbattles.kits.KitDisplayNames;
import com.cookiebuild.microbattles.kits.KitLevel;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.kits.TieredKit;
import com.cookiebuild.microbattles.ui.KitDisplayInfo;
import com.cookiebuild.cookiedough.ui.BedrockButtonText;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.BedrockMenuSessionRegistry;
import com.cookiebuild.cookiedough.ui.MainThreadPlayerAction;

public class BedrockKitSelectionUI {

    private final KitManager kitManager;
    private final MinigameProgressionService minigameStatsService;
    private final Set<UUID> menuLoads = ConcurrentHashMap.newKeySet();
    private final BedrockMenuSessionRegistry sessions = new BedrockMenuSessionRegistry();

    public BedrockKitSelectionUI(KitManager kitManager, MinigameProgressionService minigameStatsService) {
        this.kitManager = kitManager;
        // Core operations are transaction-scoped; never retain a caller-owned persistence context.
        this.minigameStatsService = new MinigameProgressionService(null);
    }

    public void open(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> open(player));
            return;
        }
        loadMenu(player, "kit menu", snapshot -> renderMainMenu(player, snapshot));
    }

    private void loadMenu(Player player, String operation, Consumer<MenuSnapshot> renderer) {
        UUID playerId = player.getUniqueId();
        if (!menuLoads.add(playerId)) {
            player.sendMessage(ChatColor.YELLOW + message(player, "microbattles.kit.menu.loading"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(MicroBattles.getInstance(), () -> {
            try {
                MenuSnapshot snapshot = loadSnapshot(playerId);
                Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
                    try {
                        if (renderEligible(Bukkit.isPrimaryThread(), player.isOnline())) renderer.accept(snapshot);
                    } catch (RuntimeException error) {
                        reportLoadFailure(player, playerId, operation, error);
                    } finally {
                        menuLoads.remove(playerId);
                    }
                });
            } catch (RuntimeException error) {
                Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
                    try {
                        reportLoadFailure(player, playerId, operation, error);
                    } finally {
                        menuLoads.remove(playerId);
                    }
                });
            }
        });
    }

    private MenuSnapshot loadSnapshot(UUID playerId) {
        int playerCoins = minigameStatsService.getCoins(playerId, MinigameProgressionService.MICROBATTLES);
        com.cookiebuild.cookiedough.model.MinigameProgression progressionStats = minigameStatsService
                .getOrCreateStats(playerId, MinigameProgressionService.MICROBATTLES);
        return new MenuSnapshot(progressionStats.getLevel(), playerCoins, progressionStats.getExperience(),
                progressionStats.getExperienceForNextLevel(), List.copyOf(kitManager.getWeeklyFreeKits()),
                getKitDisplayInfos(progressionStats, playerCoins));
    }

    private void renderMainMenu(Player player, MenuSnapshot snapshot) {
        UUID playerId = player.getUniqueId();
        String statsContent = message(player, "microbattles.kit.bedrock.stats", snapshot.playerLevel(),
                snapshot.playerCoins(), snapshot.playerXp(), snapshot.xpForNextLevel(),
                snapshot.weeklyFreeKits().stream()
                        .map(name -> KitDisplayNames.localized(name, player.locale()))
                        .collect(Collectors.joining(" / ")));
        List<KitDisplayInfo> ownedKits = snapshot.kits().stream()
                .filter(k -> k.unlocked).collect(Collectors.toList());

        String scope = "microbattles:kits";
        UUID nonce = sessions.issue(playerId, scope);
        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§l§9" + message(player, "microbattles.kit.menu.title"))
                .content(statsContent);

        // Each kit is an actionable row; headings live in content instead of becoming fake buttons.
        List<KitDisplayInfo> buttonMapping = new ArrayList<>();
        ownedKits.forEach(kit -> {
            BedrockFormImages.button(formBuilder, createOwnedKitButtonText(player, kit), imageId(kit.kitName));
            buttonMapping.add(kit);
        });
        int shopIndex = buttonMapping.size();
        BedrockFormImages.button(formBuilder, BedrockButtonText.format(
                        message(player, "microbattles.kit.shop.title")),
                "actions/shop");
        buttonMapping.add(null);
        int closeIndex = buttonMapping.size();
        BedrockFormImages.button(formBuilder, BedrockButtonText.format(
                        message(player, "microbattles.kit.close")),
                "actions/close");
        buttonMapping.add(null);

        formBuilder.validResultHandler(response -> {
            int buttonId = response.getClickedButtonId();

            MainThreadPlayerAction.dispatch(MicroBattles.getInstance(), player, () -> {
                if (!sessions.consume(playerId, nonce, scope)) return;
                if (buttonId == shopIndex) {
                    openShopMenu(player);
                } else if (buttonId != closeIndex && buttonId >= 0 && buttonId < buttonMapping.size()) {
                    KitDisplayInfo selectedKit = buttonMapping.get(buttonId);
                    if (selectedKit != null) handleKitSelection(player, selectedKit);
                }
            });
        });
        formBuilder.closedOrInvalidResultHandler(() -> sessions.invalidate(playerId, nonce, scope));
        if (!sendForm(player, formBuilder)) sessions.invalidate(playerId, nonce, scope);
    }

    private void openShopMenu(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> openShopMenu(player));
            return;
        }
        loadMenu(player, "kit shop", snapshot -> renderShopMenu(player, snapshot));
    }

    private void renderShopMenu(Player player, MenuSnapshot snapshot) {
        UUID playerId = player.getUniqueId();
        String statsContent = message(player, "microbattles.kit.bedrock.shop_stats",
                snapshot.playerLevel(), snapshot.playerCoins(), snapshot.playerXp(), snapshot.xpForNextLevel());

        List<KitDisplayInfo> allKits = snapshot.kits();
        List<KitDisplayInfo> availableKits = allKits.stream().filter(k -> !k.unlocked && k.hasLevel && k.canAfford)
                .collect(Collectors.toList());
        List<KitDisplayInfo> lockedKits = allKits.stream().filter(k -> !k.unlocked && (!k.hasLevel || !k.canAfford))
                .collect(Collectors.toList());

        String scope = "microbattles:shop";
        UUID nonce = sessions.issue(playerId, scope);
        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title("§l§6" + message(player, "microbattles.kit.shop.title"))
                .content(statsContent);

        // Create buttons list for handling clicks
        List<KitDisplayInfo> buttonMapping = new ArrayList<>();

        // Add back button at the top for easy access
        BedrockFormImages.button(formBuilder, BedrockButtonText.format(
                        message(player, "microbattles.kit.shop.back")),
                "actions/back");
        buttonMapping.add(null); // Back button

        availableKits.forEach(kit -> {
            BedrockFormImages.button(formBuilder, createShopKitButtonText(player, kit), imageId(kit.kitName));
            buttonMapping.add(kit);
        });
        lockedKits.forEach(kit -> {
            BedrockFormImages.button(formBuilder, createShopKitButtonText(player, kit), imageId(kit.kitName));
            buttonMapping.add(kit);
        });
        int closeIndex = buttonMapping.size();
        BedrockFormImages.button(formBuilder, BedrockButtonText.format(
                        message(player, "microbattles.kit.close")),
                "actions/close");
        buttonMapping.add(null);

        formBuilder.validResultHandler(response -> {
            int buttonId = response.getClickedButtonId();

            MainThreadPlayerAction.dispatch(MicroBattles.getInstance(), player, () -> {
                if (!sessions.consume(playerId, nonce, scope)) return;
                if (buttonId == 0) {
                    open(player);
                } else if (buttonId != closeIndex && buttonId >= 0 && buttonId < buttonMapping.size()) {
                    KitDisplayInfo selectedKit = buttonMapping.get(buttonId);
                    if (selectedKit != null) handleShopKitSelection(player, selectedKit);
                }
            });
        });
        formBuilder.closedOrInvalidResultHandler(() -> sessions.invalidate(playerId, nonce, scope));
        if (!sendForm(player, formBuilder)) sessions.invalidate(playerId, nonce, scope);
    }

    private String createOwnedKitButtonText(Player player, KitDisplayInfo kit) {
        String displayName = KitDisplayNames.localizedTier(kit.kitName, kit.level, player.locale());
        String rotation = kit.level == 1 && kitManager.isWeeklyFreeKit(kit.kitName) ? " §6★" : "";
        return BedrockButtonText.format("✓ " + displayName + rotation,
                message(player, "microbattles.kit.select"));
    }

    private String createShopKitButtonText(Player player, KitDisplayInfo kit) {
        String displayName = KitDisplayNames.localizedTier(kit.kitName, kit.level, player.locale());
        if (kit.hasLevel && kit.canAfford) {
            return BedrockButtonText.format(displayName,
                    message(player, "microbattles.kit.price", kit.kitLevel.getPrice()));
        } else {
            String requirement = !kit.hasLevel
                    ? message(player, "microbattles.kit.requires_level", kit.kitLevel.getRequiredLevel())
                    : message(player, "microbattles.kit.not_enough_coins");
            return BedrockButtonText.format("[" + message(player, "microbattles.kit.locked_label") + "] "
                    + displayName, requirement);
        }
    }

    private void handleKitSelection(Player player, KitDisplayInfo kitInfo) {
        String displayName = KitDisplayNames.localizedTier(
                kitInfo.kitName, kitInfo.level, player.locale());
        if (kitInfo.level == 0) {
            mutateKit(player, kitInfo, false, false);
            return;
        }
        String scope = "microbattles:kit:" + imageId(kitInfo.kitName) + ":" + kitInfo.level;
        UUID nonce = sessions.issue(player.getUniqueId(), scope);
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§9" + displayName)
                .content(kitManager.getKitDescription(kitInfo.kitName, player)
                        + "\n\n" + message(player, "microbattles.kit.preview.detail"))
                .validResultHandler(response -> {
                    int index = response.getClickedButtonId();
                    MainThreadPlayerAction.dispatch(MicroBattles.getInstance(), player, () -> {
                        if (!sessions.consume(player.getUniqueId(), nonce, scope)) return;
                        if (index == 0) {
                            mutateKit(player, kitInfo, false, false);
                        } else if (index == 1) {
                            if (!kitManager.previewKit(player, kitInfo.kitName, kitInfo.level)) {
                                player.sendMessage(ChatColor.RED + message(
                                        player, "microbattles.kit.preview.unavailable"));
                            }
                        } else if (index == 2) open(player);
                    });
                });
        form.closedOrInvalidResultHandler(() ->
                sessions.invalidate(player.getUniqueId(), nonce, scope));
        BedrockFormImages.button(form, BedrockButtonText.format(
                        message(player, "microbattles.kit.select.button")),
                imageId(kitInfo.kitName));
        BedrockFormImages.button(form, BedrockButtonText.format(
                        message(player, "microbattles.kit.preview.button")),
                "actions/preview");
        BedrockFormImages.button(form, BedrockButtonText.format(message(player, "microbattles.kit.back")),
                "actions/back");
        if (!sendForm(player, form)) sessions.invalidate(player.getUniqueId(), nonce, scope);
    }

    private void handleShopKitSelection(Player player, KitDisplayInfo kitInfo) {
        openPurchaseConfirmationForm(player, kitInfo);
    }

    private void openPurchaseConfirmationForm(Player player, KitDisplayInfo kitInfo) {
        boolean purchasable = kitInfo.hasLevel && kitInfo.canAfford;
        List<String> actions = new ArrayList<>();
        String scope = "microbattles:purchase:" + imageId(kitInfo.kitName) + ":" + kitInfo.level;
        UUID nonce = sessions.issue(player.getUniqueId(), scope);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title("§l§e" + KitDisplayNames.localizedTier(
                        kitInfo.kitName, kitInfo.level, player.locale()))
                .content(kitManager.getKitDescription(kitInfo.kitName, player) + "\n\n§6"
                        + message(player, "microbattles.kit.price", kitInfo.kitLevel.getPrice())
                        + (purchasable ? "" : "\n§c" + message(
                                player, "microbattles.kit.requirements_not_met")));
        if (purchasable) {
            BedrockFormImages.button(builder, BedrockButtonText.format(
                            message(player, "microbattles.kit.purchase_select")),
                    "actions/purchase");
            actions.add("purchase");
        }
        BedrockFormImages.button(builder, BedrockButtonText.format(
                        message(player, "microbattles.kit.preview.button")),
                "actions/preview");
        actions.add("preview");
        BedrockFormImages.button(builder, BedrockButtonText.format(message(player, "microbattles.kit.back")),
                "actions/back");
        actions.add("back");
        builder.validResultHandler(response -> {
                    int index = response.getClickedButtonId();
                    MainThreadPlayerAction.dispatch(MicroBattles.getInstance(), player, () -> {
                        if (!sessions.consume(player.getUniqueId(), nonce, scope)
                                || index < 0 || index >= actions.size()) return;
                        String action = actions.get(index);
                        if (action.equals("purchase")) {
                            mutateKit(player, kitInfo, true, true);
                        } else if (action.equals("preview")) {
                            if (!kitManager.previewKit(player, kitInfo.kitName, kitInfo.level)) {
                                player.sendMessage(ChatColor.RED + message(
                                        player, "microbattles.kit.preview.unavailable"));
                            }
                        } else openShopMenu(player);
                    });
                });
        builder.closedOrInvalidResultHandler(() ->
                sessions.invalidate(player.getUniqueId(), nonce, scope));
        if (!sendForm(player, builder)) sessions.invalidate(player.getUniqueId(), nonce, scope);
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
                String displayName = KitDisplayNames.localizedTier(
                        kitInfo.kitName, kitInfo.level, player.locale());
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

    private List<KitDisplayInfo> getKitDisplayInfos(
            com.cookiebuild.cookiedough.model.MinigameProgression progression, int coins) {
        List<KitDisplayInfo> kits = new ArrayList<>();
        Kit defaultKit = kitManager.getOriginalKit("Default");

        if (defaultKit != null) {
            kits.add(new KitDisplayInfo(defaultKit.getName(), 0, true, true, true, defaultKit, null));
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
                        tieredKit, level, null));
            }
        }

        // --- SORTING ---
        kits.sort(Comparator.comparing((KitDisplayInfo k) -> k.kitName).thenComparingInt(k -> k.level));
        return kits;
    }

    public void invalidate(Player player) {
        menuLoads.remove(player.getUniqueId());
        sessions.invalidate(player.getUniqueId());
    }

    private void reportLoadFailure(Player player, UUID playerId, String operation, RuntimeException error) {
        MicroBattles.getInstance().getLogger().warning(
                "Could not load Bedrock " + operation + " for " + playerId + ": " + error.getMessage());
        if (player.isOnline()) {
            player.sendMessage(ChatColor.RED + message(player, "microbattles.kit.menu.failed"));
        }
    }

    static boolean renderEligible(boolean primaryThread, boolean online) {
        return primaryThread && online;
    }

    private boolean sendForm(Player player, SimpleForm.Builder form) {
        FloodgatePlayer fgPlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (fgPlayer == null) return false;
        fgPlayer.sendForm(form.build());
        return true;
    }

    private record MenuSnapshot(int playerLevel, int playerCoins, int playerXp, int xpForNextLevel,
            List<String> weeklyFreeKits, List<KitDisplayInfo> kits) {
        private MenuSnapshot {
            weeklyFreeKits = List.copyOf(weeklyFreeKits);
            kits = List.copyOf(kits);
        }
    }

}
