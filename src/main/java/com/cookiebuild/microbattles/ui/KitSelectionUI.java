package com.cookiebuild.microbattles.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.kits.KitLevel;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.kits.TieredKit;
import com.cookiebuild.microbattles.ui.bedrock.BedrockKitSelectionUI;
import com.cookiebuild.microbattles.ui.bedrock.BedrockUIHelper;
import com.cookiebuild.cookiedough.ui.MenuLore;

/** Paginated Java selector with stable item metadata and async snapshot loading. */
public final class KitSelectionUI implements Listener {
    private static final int PAGE_SIZE = 36;
    private final KitManager kitManager;
    private final BedrockKitSelectionUI bedrockKitSelectionUI;
    private final NamespacedKey kitNameKey;
    private final NamespacedKey kitLevelKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey pageKey;
    private final Set<UUID> menuLoads = ConcurrentHashMap.newKeySet();

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
        if (!menuLoads.add(playerId)) {
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage(
                    "microbattles.kit.menu.loading", player.locale()));
            return;
        }
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
                        player.sendMessage(ChatColor.RED + LocaleManager.getMessage(
                                "microbattles.kit.menu.failed", player.locale())));
            } finally {
                menuLoads.remove(playerId);
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
        KitMenuHolder holder = new KitMenuHolder(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                titlePrefix(player) + (page + 1) + "/" + pageCount);
        holder.bind(inventory);
        inventory.setItem(4, playerInfo(player, snapshot));
        inventory.setItem(7, rotationInfo(player, snapshot.rotation()));

        List<Entry> pageEntries = KitPagination.page(snapshot.entries(), page, PAGE_SIZE);
        for (int index = 0; index < pageEntries.size(); index++) {
            inventory.setItem(9 + index, kitItem(pageEntries.get(index), player));
        }
        if (page > 0) inventory.setItem(45, actionItem(player, Material.ARROW,
                "microbattles.kit.previous", "previous", page - 1));
        inventory.setItem(49, actionItem(player, Material.BARRIER,
                "microbattles.kit.close", "close", page));
        if (page + 1 < pageCount) inventory.setItem(53, actionItem(player, Material.ARROW,
                "microbattles.kit.next", "next", page + 1));
        player.openInventory(inventory);
    }

    private ItemStack playerInfo(Player player, MenuSnapshot snapshot) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + message(player, "microbattles.kit.stats.title"));
        double kd = snapshot.deaths() == 0 ? snapshot.kills() : (double) snapshot.kills() / snapshot.deaths();
        meta.setLore(List.of(
                ChatColor.YELLOW + message(player, "microbattles.kit.stats.level", snapshot.level()),
                ChatColor.GOLD + message(player, "microbattles.kit.stats.coins", snapshot.coins()),
                ChatColor.GREEN + message(player, "microbattles.kit.stats.xp",
                        snapshot.experience(), snapshot.nextLevelExperience()),
                ChatColor.BLUE + message(player, "microbattles.kit.stats.wins", snapshot.wins()),
                ChatColor.RED + message(player, "microbattles.kit.stats.losses", snapshot.losses()),
                ChatColor.GREEN + message(player, "microbattles.kit.stats.kills", snapshot.kills()),
                ChatColor.DARK_RED + message(player, "microbattles.kit.stats.deaths", snapshot.deaths()),
                ChatColor.YELLOW + message(player, "microbattles.kit.stats.kd",
                        String.format(java.util.Locale.ROOT, "%.2f", kd))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rotationInfo(Player player, List<String> rotation) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + message(player, "microbattles.kit.rotation.title"));
        List<String> lore = new ArrayList<>();
        lore.add(MenuLore.legacyDetail(message(player, "microbattles.kit.rotation.detail")));
        rotation.forEach(name -> lore.add(ChatColor.AQUA + "• " + name));
        lore.add(MenuLore.legacyDetail(message(player, "microbattles.kit.rotation.schedule")));
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
        if (entry.weekly()) lore.add(ChatColor.GOLD + message(player, "microbattles.kit.weekly"));
        else if (entry.owned()) lore.add(ChatColor.GREEN + message(player, "microbattles.kit.owned"));
        else lore.add(ChatColor.GOLD + message(player, "microbattles.kit.price", entry.price()));
        if (!entry.levelReady()) lore.add(ChatColor.RED + message(
                player, "microbattles.kit.requires_level", entry.requiredLevel()));
        if (!entry.prerequisiteReady()) lore.add(ChatColor.RED + message(
                player, "microbattles.kit.requires_previous"));
        lore.add("");
        lore.add(MenuLore.legacyDetail(kitManager.getKitDescription(entry.name(), player)));
        lore.add("");
        lore.add(entry.owned() ? ChatColor.GREEN + message(player, "microbattles.kit.select")
                : ChatColor.YELLOW + message(player, "microbattles.kit.purchase"));
        if (entry.level() > 0) lore.add(ChatColor.AQUA + message(player, "microbattles.kit.preview.action"));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(kitNameKey, PersistentDataType.STRING, entry.name());
        meta.getPersistentDataContainer().set(kitLevelKey, PersistentDataType.INTEGER, entry.level());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(Player player, Material material, String key, String action, int page) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + message(player, key));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof KitMenuHolder holder)
                || !holder.playerId().equals(player.getUniqueId())) return;
        event.setCancelled(true);
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
                player.sendMessage(ChatColor.RED + message(player, "microbattles.kit.preview.unavailable"));
            }
            return;
        }
        if (level == 0) {
            player.closeInventory();
            mutateSelection(player, "Default", 0, false, holder.page());
            return;
        }
        player.closeInventory();
        mutateSelection(player, kitName, level, true, holder.page());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof KitMenuHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (dragTouchesTop(event.getRawSlots(), topSize)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menuLoads.remove(event.getPlayer().getUniqueId());
        bedrockKitSelectionUI.invalidate(event.getPlayer());
    }

    static boolean dragTouchesTop(java.util.Set<Integer> rawSlots, int topSize) {
        return rawSlots.stream().anyMatch(slot -> slot >= 0 && slot < topSize);
    }

    private void mutateSelection(Player player, String kitName, int level, boolean allowPurchase, int page) {
        UUID playerId = player.getUniqueId();
        if (!kitManager.tryBeginMutation(playerId)) {
            player.sendMessage(ChatColor.YELLOW + LocaleManager.getMessage(
                    "microbattles.kit.action.busy", player.locale()));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(MicroBattles.getInstance(), () -> {
            boolean purchased = false;
            boolean success = false;
            try {
                MinigameProgressionService service = new MinigameProgressionService(null);
                if (level == 0 || kitManager.isKitLevelUnlocked(service, playerId, kitName, level)) {
                    kitManager.selectKit(playerId, kitName, level);
                    success = true;
                } else if (allowPurchase && kitManager.purchaseKitLevel(service, playerId, kitName, level)) {
                    kitManager.selectKit(playerId, kitName, level);
                    purchased = true;
                    success = true;
                }
            } catch (RuntimeException error) {
                MicroBattles.getInstance().getLogger().warning(
                        "Could not update MicroBattles kit for " + playerId + ": " + error.getMessage());
            } finally {
                kitManager.finishMutation(playerId);
            }
            boolean selected = success;
            boolean bought = purchased;
            Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
                if (!player.isOnline()) return;
                String display = level == 0 ? kitName : kitName + " " + roman(level);
                String key = selected
                        ? bought ? "microbattles.kit.action.purchased" : "microbattles.kit.action.selected"
                        : "microbattles.kit.action.failed";
                player.sendMessage((selected ? ChatColor.GREEN : ChatColor.RED)
                        + LocaleManager.getMessage(key, player.locale(), display));
                if (!selected) openKitSelectionGUI(player, page);
            });
        });
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

    private static String message(Player player, String key, Object... arguments) {
        return LocaleManager.getMessage(key, player.locale(), arguments);
    }

    private static String titlePrefix(Player player) {
        return ChatColor.DARK_AQUA + message(player, "microbattles.kit.menu.title") + " • ";
    }

    private record Entry(String name, int level, boolean owned, boolean weekly, boolean levelReady,
            boolean prerequisiteReady, int price, int requiredLevel) { }

    private record MenuSnapshot(int level, int experience, int nextLevelExperience, int coins, int wins,
            int losses, int kills, int deaths, List<String> rotation, List<Entry> entries) { }
}
