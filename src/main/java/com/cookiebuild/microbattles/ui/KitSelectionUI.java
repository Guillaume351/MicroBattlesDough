package com.cookiebuild.microbattles.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.kits.KitLevel;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.kits.TieredKit;
import com.cookiebuild.microbattles.ui.bedrock.BedrockKitSelectionUI;
import com.cookiebuild.microbattles.ui.bedrock.BedrockUIHelper;

/** Paginated Java selector with stable item metadata and async snapshot loading. */
public final class KitSelectionUI implements Listener {
    private static final String TITLE_PREFIX = ChatColor.DARK_AQUA + "Kit Selection • ";
    private static final int PAGE_SIZE = 36;
    private final KitManager kitManager;
    private final BedrockKitSelectionUI bedrockKitSelectionUI;
    private final NamespacedKey kitNameKey;
    private final NamespacedKey kitLevelKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey pageKey;

    public KitSelectionUI(KitManager kitManager, MinigameProgressionService ignoredService,
            PlayerStatsService ignoredPlayerStatsService) {
        this.kitManager = kitManager;
        this.bedrockKitSelectionUI = new BedrockKitSelectionUI(kitManager, ignoredService);
        this.kitNameKey = new NamespacedKey(MicroBattles.getInstance(), "kit_name");
        this.kitLevelKey = new NamespacedKey(MicroBattles.getInstance(), "kit_level");
        this.actionKey = new NamespacedKey(MicroBattles.getInstance(), "kit_action");
        this.pageKey = new NamespacedKey(MicroBattles.getInstance(), "kit_page");
    }

    public void openKitSelectionGUI(Player player) {
        openKitSelectionGUI(player, 0);
    }

    private void openKitSelectionGUI(Player player, int requestedPage) {
        if (BedrockUIHelper.isBedrockPlayer(player)) {
            bedrockKitSelectionUI.open(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(MicroBattles.getInstance(), () -> {
            try {
                MenuSnapshot snapshot = loadSnapshot(playerId);
                Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
                    if (player.isOnline()) {
                        render(player, snapshot, requestedPage);
                    }
                });
            } catch (RuntimeException exception) {
                MicroBattles.getInstance().getLogger().warning(
                        "Could not load kit menu for " + playerId + ": " + exception.getMessage());
                Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () ->
                        player.sendMessage(ChatColor.RED + "The kit menu could not be loaded. Please try again."));
            }
        });
    }

    private MenuSnapshot loadSnapshot(UUID playerId) {
        MinigameProgressionService service = new MinigameProgressionService(null);
        MinigameProgression progression = service.getOrCreateStats(playerId, MinigameProgressionService.MICROBATTLES);
        int coins = service.getCoins(playerId, MinigameProgressionService.MICROBATTLES);
        List<PlayerMatchPerformance> performances = PlayerStatsService.getPlayerPerformancesStatic(playerId).stream()
                .filter(performance -> "MicroBattles".equals(performance.getMatch().getGameType())).toList();
        int wins = (int) performances.stream()
                .filter(performance -> performance.getMatch().getWinners().stream()
                        .anyMatch(winner -> winner.getId().equals(playerId)))
                .count();
        int kills = performances.stream().mapToInt(PlayerMatchPerformance::getKillsInMatch).sum();
        int deaths = performances.stream().mapToInt(PlayerMatchPerformance::getDeathsInMatch).sum();

        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("Default", 0, true, true, true, true, 0, 0));
        List<TieredKit> kits = kitManager.getAllTieredKits().stream()
                .sorted(Comparator.comparing(TieredKit::getBaseName)).toList();
        for (TieredKit kit : kits) {
            for (KitLevel level : kit.getLevels()) {
                boolean weekly = level.getLevel() == 1 && kitManager.isWeeklyFreeKit(kit.getBaseName());
                boolean owned = level.isDefaultUnlocked() || weekly
                        || progression.hasUnlockedKit(kit.getBaseName() + ":L" + level.getLevel());
                boolean prerequisite = level.getLevel() == 1
                        || progression.hasUnlockedKit(kit.getBaseName() + ":L" + (level.getLevel() - 1))
                        || (level.getLevel() == 2 && weekly);
                entries.add(new Entry(kit.getBaseName(), level.getLevel(), owned, weekly,
                        progression.getLevel() >= level.getRequiredLevel(), prerequisite,
                        level.getPrice(), level.getRequiredLevel()));
            }
        }
        return new MenuSnapshot(progression.getLevel(), progression.getExperience(),
                progression.getExperienceForNextLevel(), coins, wins, performances.size() - wins, kills, deaths,
                kitManager.getWeeklyFreeKits(), List.copyOf(entries));
    }

    private void render(Player player, MenuSnapshot snapshot, int requestedPage) {
        int pageCount = KitPagination.pageCount(snapshot.entries().size(), PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        Inventory inventory = Bukkit.createInventory(null, 54,
                TITLE_PREFIX + (page + 1) + "/" + pageCount);
        inventory.setItem(4, playerInfo(snapshot));
        inventory.setItem(7, rotationInfo(snapshot.rotation()));

        List<Entry> pageEntries = KitPagination.page(snapshot.entries(), page, PAGE_SIZE);
        for (int index = 0; index < pageEntries.size(); index++) {
            inventory.setItem(9 + index, kitItem(pageEntries.get(index), player));
        }
        if (page > 0) inventory.setItem(45, actionItem(Material.ARROW, "Previous page", "previous", page - 1));
        inventory.setItem(49, actionItem(Material.BARRIER, "Close", "close", page));
        if (page + 1 < pageCount) inventory.setItem(53, actionItem(Material.ARROW, "Next page", "next", page + 1));
        player.openInventory(inventory);
    }

    private ItemStack playerInfo(MenuSnapshot snapshot) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Your MicroBattles Stats");
        double kd = snapshot.deaths() == 0 ? snapshot.kills() : (double) snapshot.kills() / snapshot.deaths();
        meta.setLore(List.of(
                ChatColor.YELLOW + "Level: " + snapshot.level(),
                ChatColor.GOLD + "Coins: " + snapshot.coins(),
                ChatColor.GREEN + "XP: " + snapshot.experience() + "/" + snapshot.nextLevelExperience(),
                ChatColor.BLUE + "Wins: " + snapshot.wins(),
                ChatColor.RED + "Losses: " + snapshot.losses(),
                ChatColor.GREEN + "Kills: " + snapshot.kills(),
                ChatColor.DARK_RED + "Deaths: " + snapshot.deaths(),
                ChatColor.YELLOW + String.format(java.util.Locale.ROOT, "K/D: %.2f", kd)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rotationInfo(List<String> rotation) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Weekly Free Rotation");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Tier I is free to select this week:");
        rotation.forEach(name -> lore.add(ChatColor.AQUA + "• " + name));
        lore.add(ChatColor.DARK_GRAY + "Rotation changes every ISO week.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack kitItem(Entry entry, Player player) {
        ItemStack item = new ItemStack(getKitMaterial(entry.name()));
        ItemMeta meta = item.getItemMeta();
        String display = entry.level() == 0 ? entry.name() : entry.name() + " " + roman(entry.level());
        ChatColor color = entry.owned() ? ChatColor.GREEN
                : entry.levelReady() && entry.prerequisiteReady() ? ChatColor.YELLOW : ChatColor.RED;
        meta.setDisplayName(ChatColor.RESET + color.toString() + display);
        List<String> lore = new ArrayList<>();
        if (entry.weekly()) lore.add(ChatColor.GOLD + "★ Weekly free rotation");
        else if (entry.owned()) lore.add(ChatColor.GREEN + "✓ Owned");
        else lore.add(ChatColor.GOLD + "Price: " + entry.price() + " coins");
        if (!entry.levelReady()) lore.add(ChatColor.RED + "Requires level " + entry.requiredLevel());
        if (!entry.prerequisiteReady()) lore.add(ChatColor.RED + "Unlock the previous tier first");
        lore.add("");
        lore.add(ChatColor.GRAY + kitManager.getKitDescription(entry.name(), player));
        lore.add("");
        lore.add(entry.owned() ? ChatColor.GREEN + "Left-click to select"
                : ChatColor.YELLOW + "Left-click to purchase");
        if (entry.level() > 0) lore.add(ChatColor.AQUA + "Right-click to practice-preview");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(kitNameKey, PersistentDataType.STRING, entry.name());
        meta.getPersistentDataContainer().set(kitLevelKey, PersistentDataType.INTEGER, entry.level());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(Material material, String name, String action, int page) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action != null) {
            if (action.equals("close")) player.closeInventory();
            else openKitSelectionGUI(player,
                    meta.getPersistentDataContainer().getOrDefault(pageKey, PersistentDataType.INTEGER, 0));
            return;
        }
        String kitName = meta.getPersistentDataContainer().get(kitNameKey, PersistentDataType.STRING);
        Integer level = meta.getPersistentDataContainer().get(kitLevelKey, PersistentDataType.INTEGER);
        if (kitName == null || level == null) return;
        if (event.isRightClick() && level > 0) {
            player.closeInventory();
            if (!kitManager.previewKit(player, kitName, level)) {
                player.sendMessage(ChatColor.RED + "Kit preview is only available before your match starts.");
            }
            return;
        }
        if (level == 0) {
            kitManager.selectKit(player.getUniqueId(), "Default", 0);
            player.sendMessage(ChatColor.GREEN + "Selected Default.");
            player.closeInventory();
            return;
        }
        MinigameProgressionService service = new MinigameProgressionService(null);
        if (kitManager.isKitLevelUnlocked(service, player.getUniqueId(), kitName, level)) {
            kitManager.selectKit(player.getUniqueId(), kitName, level);
            player.sendMessage(ChatColor.GREEN + "Selected " + kitName + " " + roman(level) + ".");
            player.closeInventory();
        } else if (kitManager.purchaseKitLevel(service, player.getUniqueId(), kitName, level)) {
            kitManager.selectKit(player.getUniqueId(), kitName, level);
            player.sendMessage(ChatColor.GREEN + "Purchased and selected " + kitName + " " + roman(level) + ".");
            player.closeInventory();
        } else {
            player.sendMessage(ChatColor.RED + "Purchase failed: check your coins, level and previous tier.");
            openKitSelectionGUI(player, currentPage(event.getView().getTitle()));
        }
    }

    private int currentPage(String title) {
        try {
            String suffix = title.substring(TITLE_PREFIX.length());
            return Math.max(0, Integer.parseInt(suffix.split("/", 2)[0]) - 1);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private Material getKitMaterial(String name) {
        return switch (name) {
            case "Default" -> Material.STONE_SWORD;
            case "Archer" -> Material.BOW;
            case "Miner" -> Material.IRON_PICKAXE;
            case "Trapper" -> Material.COBWEB;
            case "Knockback Warrior" -> Material.STICK;
            case "Berserker" -> Material.STONE_AXE;
            case "Explosive Archer" -> Material.TNT;
            case "Alchemist" -> Material.BREWING_STAND;
            case "Vampire" -> Material.REDSTONE;
            case "Tank" -> Material.SHIELD;
            case "Enderman" -> Material.ENDER_PEARL;
            case "Ninja" -> Material.LEATHER_BOOTS;
            case "Chemist" -> Material.POTION;
            case "Frost Mage" -> Material.SNOWBALL;
            case "Assassin" -> Material.GOLDEN_SWORD;
            case "Juggernaut" -> Material.CHAINMAIL_CHESTPLATE;
            case "Mobility" -> Material.FEATHER;
            default -> Material.CHEST;
        };
    }

    private String roman(int level) {
        return switch (level) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> ""; };
    }

    private record Entry(String name, int level, boolean owned, boolean weekly, boolean levelReady,
            boolean prerequisiteReady, int price, int requiredLevel) { }

    private record MenuSnapshot(int level, int experience, int nextLevelExperience, int coins, int wins,
            int losses, int kills, int deaths, List<String> rotation, List<Entry> entries) { }
}
