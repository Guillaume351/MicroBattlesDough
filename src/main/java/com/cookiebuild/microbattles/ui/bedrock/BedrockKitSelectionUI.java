package com.cookiebuild.microbattles.ui.bedrock;

import java.util.ArrayList;
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

    private enum KitListCategory {
        OWNED, AVAILABLE, LOCKED
    }

    private final KitManager kitManager;
    private final MinigameStatsService minigameStatsService;

    public BedrockKitSelectionUI(KitManager kitManager, MinigameStatsService minigameStatsService) {
        this.kitManager = kitManager;
        this.minigameStatsService = minigameStatsService;
    }

    public void open(Player player) {
        openMainMenu(player);
    }

    private void openMainMenu(Player player) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Kit Selection")
                .content("Welcome! Please choose a category.")
                .button("§bMy Stats")
                .button("§aOwned Kits")
                .button("§eAvailable for Purchase")
                .button("§cLocked Kits");

        form.validResultHandler(response -> {
            switch (response.getClickedButtonId()) {
                case 0:
                    openStatsForm(player);
                    break;
                case 1:
                    openKitListForm(player, KitListCategory.OWNED);
                    break;
                case 2:
                    openKitListForm(player, KitListCategory.AVAILABLE);
                    break;
                case 3:
                    openKitListForm(player, KitListCategory.LOCKED);
                    break;
            }
        });

        sendForm(player, form);
    }

    private void openStatsForm(Player player) {
        int playerLevel = minigameStatsService.getLevel(player.getUniqueId(), MinigameStatsService.MICROBATTLES);
        int playerCoins = minigameStatsService.getCoins(player.getUniqueId(), MinigameStatsService.MICROBATTLES);
        int playerXp = minigameStatsService.getOrCreateStats(player.getUniqueId(), MinigameStatsService.MICROBATTLES)
                .getExperience();

        CustomForm form = CustomForm.builder()
                .title("Your MicroBattles Stats")
                .label(String.format("§eLevel: §f%d\n§6Coins: §f%d\n§aXP: §f%d", playerLevel, playerCoins, playerXp))
                .build();

        sendForm(player, form);
    }

    private void openKitListForm(Player player, KitListCategory category) {
        List<KitDisplayInfo> kits = getKitDisplayInfos(player).stream()
                .filter(kit -> {
                    switch (category) {
                        case OWNED:
                            return kit.unlocked;
                        case AVAILABLE:
                            return !kit.unlocked && kit.hasLevel && kit.canAfford;
                        case LOCKED:
                            return !kit.unlocked && (!kit.hasLevel || !kit.canAfford);
                        default:
                            return false;
                    }
                })
                .collect(Collectors.toList());

        if (kits.isEmpty()) {
            ModalForm form = ModalForm.builder()
                    .title(category.toString() + " Kits")
                    .content("You have no kits in this category.")
                    .button1("§aOK")
                    .button2("§cBack")
                    .validResultHandler(response -> {
                        if (response.getClickedButtonId() == 1)
                            openMainMenu(player);
                    })
                    .build();
            sendForm(player, form);
            return;
        }

        SimpleForm.Builder formBuilder = SimpleForm.builder().title(category.toString() + " Kits");
        kits.forEach(kit -> formBuilder.button(createButtonText(kit)));
        formBuilder.closedOrInvalidResultHandler(() -> openMainMenu(player));
        formBuilder.validResultHandler(response -> handleKitSelection(player, kits.get(response.getClickedButtonId())));

        sendForm(player, formBuilder);
    }

    private String createButtonText(KitDisplayInfo kit) {
        String displayName = kit.level == 0 ? kit.kitName : kit.kitLevel.getDisplayName(kit.kitName);
        if (kit.unlocked) {
            return "§a" + displayName;
        } else if (kit.hasLevel && kit.canAfford) {
            return String.format("§e%s\n§7Price: %d coins", displayName, kit.kitLevel.getPrice());
        } else {
            String requirement = !kit.hasLevel ? "Lvl " + kit.kitLevel.getRequiredLevel() : "Not enough coins";
            return String.format("§c%s\n§8%s", displayName, requirement);
        }
    }

    private void handleKitSelection(Player player, KitDisplayInfo kitInfo) {
        if (kitInfo.unlocked) {
            kitManager.selectKit(player.getUniqueId(), kitInfo.kitName, kitInfo.level);
            player.sendMessage(ChatColor.GREEN + "Selected kit: " + kitInfo.kitLevel.getDisplayName(kitInfo.kitName));
        } else if (kitInfo.hasLevel && kitInfo.canAfford) {
            openPurchaseConfirmationForm(player, kitInfo);
        } else {
            player.sendMessage(ChatColor.RED + "You do not meet the requirements for this kit.");
            openKitListForm(player, KitListCategory.LOCKED);
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
                            openKitListForm(player, KitListCategory.OWNED);
                        } else {
                            player.sendMessage(ChatColor.RED + "Purchase failed. Please try again.");
                        }
                    } else {
                        openKitListForm(player, KitListCategory.AVAILABLE);
                    }
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
        return kits;
    }

    private void sendForm(Player player, SimpleForm.Builder form) {
        FloodgatePlayer fgPlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (fgPlayer != null)
            fgPlayer.sendForm(form);
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