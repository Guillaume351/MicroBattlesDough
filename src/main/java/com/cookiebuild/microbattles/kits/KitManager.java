package com.cookiebuild.microbattles.kits;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.listener.KitSelectorListener;

/** Kit catalog, unlock policy and balanced sidegrade loadouts. */
public final class KitManager {
    private static KitManager instance;
    private final Map<String, Kit> originalKits = new HashMap<>();
    private final Map<String, TieredKit> tieredKits = new HashMap<>();
    private final Map<UUID, String> playerSelectedKits = new HashMap<>();

    private KitManager() {
        initializeCatalog();
        initializeTieredKits();
    }

    public static synchronized KitManager getInstance() {
        if (instance == null) {
            instance = new KitManager();
        }
        return instance;
    }

    private void initializeCatalog() {
        Kit defaultKit = register("Default", 0, 0, true);
        defaultKit.addItem(Material.STONE_SWORD, 1);
        defaultKit.addItem(Material.BOW, 1);
        defaultKit.addItem(Material.GOLDEN_PICKAXE, 1);
        defaultKit.addItem(Material.ARROW, 24);
        defaultKit.addItem(Material.BREAD, 5);
        defaultKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);

        register("Explosive Archer", 500, 7, false);
        register("Enderman", 1_000, 16, false);
        register("Knockback Warrior", 300, 0, true);
        register("Tank", 900, 15, false);
        register("Ninja", 1_200, 22, false);
        register("Archer", 500, 0, true);
        register("Berserker", 600, 8, false);
        register("Chemist", 1_100, 20, false);
        register("Assassin", 1_400, 28, false);
        register("Miner", 300, 3, false);
        register("Vampire", 900, 14, false);
        register("Frost Mage", 1_300, 25, false);
        register("Juggernaut", 1_600, 30, false);
        register("Trapper", 400, 5, false);
        register("Alchemist", 800, 12, false);
        register("Mobility", 400, 4, false);
    }

    private Kit register(String name, int price, int requiredLevel, boolean defaultUnlocked) {
        String key = "kit." + name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_') + ".description";
        Kit kit = new Kit(name, price, requiredLevel, defaultUnlocked, key);
        originalKits.put(name, kit);
        return kit;
    }

    private void initializeTieredKits() {
        for (Kit kit : originalKits.values()) {
            if (kit.getName().equals("Default")) {
                continue;
            }
            TieredKit tiered = new TieredKit(kit.getName());
            boolean starter = kit.getName().equals("Knockback Warrior") || kit.getName().equals("Archer");
            tiered.addLevel(1, starter ? 0 : kit.getPrice() / 2,
                    Math.max(1, kit.getRequiredLevel() / 3), starter);
            tiered.addLevel(2, kit.getPrice(),
                    Math.max(Math.max(1, kit.getRequiredLevel() / 3) + 2, kit.getRequiredLevel() * 2 / 3), false);
            tiered.addLevel(3, kit.getPrice() * 2, kit.getRequiredLevel(), false);
            tieredKits.put(kit.getName(), tiered);
        }
    }

    public TieredKit getTieredKit(String baseName) {
        return tieredKits.get(baseName);
    }

    public Collection<TieredKit> getAllTieredKits() {
        return tieredKits.values();
    }

    public Kit getOriginalKit(String name) {
        return originalKits.get(name);
    }

    public Collection<Kit> getAllOriginalKits() {
        return originalKits.values();
    }

    public Kit getRandomOriginalKit() {
        List<Kit> kits = new ArrayList<>(originalKits.values());
        kits.removeIf(kit -> kit.getName().equals("Default"));
        return kits.isEmpty() ? originalKits.get("Default") : kits.get(new Random().nextInt(kits.size()));
    }

    public void selectKit(UUID playerId, String kitName, int level) {
        playerSelectedKits.put(playerId, kitName + ":" + level);
        CookieDough.createMinigameProgressionService().setLastSelectedKit(playerId,
                MinigameProgressionService.MICROBATTLES, kitName, level);
    }

    public void clearSelectedKit(UUID playerId) {
        playerSelectedKits.remove(playerId);
    }

    public boolean hasKitSelected(UUID playerId) {
        return playerSelectedKits.containsKey(playerId);
    }

    public String getSelectedKit(UUID playerId) {
        return playerSelectedKits.get(playerId);
    }

    public int getSelectedTier(UUID playerId) {
        String selected = playerSelectedKits.get(playerId);
        if (selected == null) {
            return 0;
        }
        String[] parts = selected.split(":", 2);
        try {
            return parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public List<String> getWeeklyFreeKits() {
        return WeeklyKitRotation.forDate(tieredKits.keySet(), LocalDate.now());
    }

    public boolean isWeeklyFreeKit(String kitName) {
        return WeeklyKitRotation.contains(tieredKits.keySet(), LocalDate.now(), kitName);
    }

    public String getKitDescription(String kitName, Player player) {
        return switch (kitName) {
            case "Default" -> "Reliable sword, bow, blocks and food.";
            case "Explosive Archer" -> "Limited arrows create a small enemy-only blast on impact.";
            case "Enderman" -> "A light melee kit with a few repositioning pearls.";
            case "Tank" -> "Shield and extra armor, traded for permanent Slowness.";
            case "Ninja" -> "Fast skirmisher with snowballs and double-sneak stealth.";
            case "Archer" -> "Long-range pressure with more arrows, but weak melee gear.";
            case "Berserker" -> "Axe fighter who briefly gains Strength below 30% health.";
            case "Chemist" -> "Carries useful single-use drinkable potions.";
            case "Assassin" -> "Double-sneak stealth empowers one carefully timed melee hit.";
            case "Miner" -> "Fast mining and extra blocks for map control, with weak combat gear.";
            case "Vampire" -> "Restores a small amount of health on enemy melee hits.";
            case "Frost Mage" -> "Snowballs slow enemies; the wand creates a short temporary ice bridge.";
            case "Juggernaut" -> "Heavy armor, axe and shield at the cost of severe Slowness.";
            case "Trapper" -> "Cobwebs and utility supplies reward controlling narrow routes.";
            case "Alchemist" -> "The brewing stand grants one random short self-buff on cooldown.";
            case "Knockback Warrior" -> "Controls ledges with Knockback I but deals little direct damage.";
            case "Mobility" -> "Permanent Speed and light gear for rotations and escapes.";
            default -> "Unknown kit.";
        };
    }

    public boolean isKitLevelUnlocked(MinigameProgressionService service, UUID playerId, String kitName, int level) {
        KitLevel kitLevel = getKitLevel(kitName, level);
        if (kitLevel == null) {
            return false;
        }
        if (kitLevel.isDefaultUnlocked() || (level == 1 && isWeeklyFreeKit(kitName))) {
            return true;
        }
        return service.hasUnlockedKit(playerId, MinigameProgressionService.MICROBATTLES, kitName + ":L" + level);
    }

    public boolean canAffordKitLevel(MinigameProgressionService service, UUID playerId, String kitName, int level) {
        KitLevel kitLevel = getKitLevel(kitName, level);
        return kitLevel != null && service.getCoins(playerId, MinigameProgressionService.MICROBATTLES)
                >= kitLevel.getPrice();
    }

    public boolean hasRequiredPlayerLevelForKit(MinigameProgressionService service, UUID playerId,
            String kitName, int level) {
        KitLevel kitLevel = getKitLevel(kitName, level);
        return kitLevel != null && service.getLevel(playerId, MinigameProgressionService.MICROBATTLES)
                >= kitLevel.getRequiredLevel();
    }

    public boolean purchaseKitLevel(MinigameProgressionService service, UUID playerId, String kitName, int level) {
        KitLevel kitLevel = getKitLevel(kitName, level);
        if (kitLevel == null || !hasRequiredPlayerLevelForKit(service, playerId, kitName, level)
                || (level > 1 && !isKitLevelUnlocked(service, playerId, kitName, level - 1))) {
            return false;
        }
        return service.purchaseAndUnlockKit(playerId, MinigameProgressionService.MICROBATTLES,
                kitName + ":L" + level, kitLevel.getPrice());
    }

    public KitLevel getKitLevel(String kitName, int level) {
        TieredKit kit = tieredKits.get(kitName);
        return kit == null ? null : kit.getLevel(level);
    }

    public void equipLastSelectedKit(Player player) {
        MinigameProgressionService service = CookieDough.createMinigameProgressionService();
        var stats = service.getOrCreateStats(player.getUniqueId(), MinigameProgressionService.MICROBATTLES);
        String kitName = stats.getLastSelectedKitName();
        int level = stats.getLastSelectedKitLevel();
        if (kitName != null && !kitName.isBlank()
                && isKitLevelUnlocked(service, player.getUniqueId(), kitName, level)) {
            equipTieredKit(player, kitName, level);
            playerSelectedKits.put(player.getUniqueId(), kitName + ":" + level);
        } else {
            originalKits.get("Default").equipPlayer(player);
            playerSelectedKits.put(player.getUniqueId(), "Default:0");
        }
    }

    public void equipSelectedKit(Player player) {
        String selected = playerSelectedKits.get(player.getUniqueId());
        if (selected == null) {
            equipLastSelectedKit(player);
            return;
        }
        String[] parts = selected.split(":", 2);
        if (parts.length == 2) {
            equipTieredKit(player, parts[0], Integer.parseInt(parts[1]));
        }
    }

    public boolean previewKit(Player player, String kitName, int level) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = cookiePlayer == null ? null : GameManager.getGameOfPlayer(cookiePlayer);
        if (!(game instanceof MicroBattlesGame) || game.hasStarted() || getKitLevel(kitName, level) == null) {
            return false;
        }
        CookieDough.getInstance().getPracticeManager().stop(player, false);
        equipTieredKit(player, kitName, level);
        CookieDough.getInstance().getPlayerHubMenu().ensureQueueControl(player);
        player.sendMessage(ChatColor.AQUA + LocaleManager.getMessage("microbattles.kit.preview",
                player.locale(), kitName, roman(level)));
        Bukkit.getScheduler().runTaskLater(MicroBattles.getInstance(), () -> {
            CookiePlayer current = PlayerManager.getPlayer(player);
            Game currentGame = current == null ? null : GameManager.getGameOfPlayer(current);
            if (currentGame == game && !game.hasStarted() && player.isOnline()) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                KitSelectorListener.giveKitSelectorCookie(player);
                CookieDough.getInstance().getPlayerHubMenu().ensureQueueControl(player);
            }
        }, 160L);
        return true;
    }

    private void equipTieredKit(Player player, String kitName, int level) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        int tier = Math.max(1, Math.min(3, level));
        switch (kitName) {
            case "Explosive Archer" -> equipExplosiveArcher(player, tier);
            case "Enderman" -> equipEnderman(player, tier);
            case "Tank" -> equipTank(player, tier);
            case "Ninja" -> equipNinja(player, tier);
            case "Archer" -> equipArcher(player, tier);
            case "Berserker" -> equipBerserker(player, tier);
            case "Chemist" -> equipChemist(player, tier);
            case "Assassin" -> equipAssassin(player, tier);
            case "Miner" -> equipMiner(player, tier);
            case "Vampire" -> equipVampire(player, tier);
            case "Frost Mage" -> equipFrostMage(player, tier);
            case "Juggernaut" -> equipJuggernaut(player, tier);
            case "Trapper" -> equipTrapper(player, tier);
            case "Alchemist" -> equipAlchemist(player, tier);
            case "Knockback Warrior" -> equipKnockbackWarrior(player, tier);
            case "Mobility" -> equipMobility(player, tier);
            default -> originalKits.get("Default").equipPlayer(player);
        }
    }

    private void equipExplosiveArcher(Player player, int tier) {
        give(player, Material.BOW, 1, Material.ARROW, 8 + tier * 4, Material.WOODEN_SWORD, 1);
        setLeatherArmor(player);
    }

    private void equipEnderman(Player player, int tier) {
        give(player, Material.STONE_SWORD, 1, Material.ENDER_PEARL, tier);
        setLeatherArmor(player);
    }

    private void equipTank(Player player, int tier) {
        give(player, Material.STONE_SWORD, 1);
        setLeatherArmor(player);
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        player.getInventory().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
        if (tier >= 2) player.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        if (tier >= 3) player.getInventory().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
        permanent(player, PotionEffectType.SLOWNESS, 0);
    }

    private void equipNinja(Player player, int tier) {
        give(player, Material.WOODEN_SWORD, 1, Material.SNOWBALL, 6 + tier * 2);
        setLeatherArmor(player);
        permanent(player, PotionEffectType.SPEED, 0);
        if (tier >= 3) permanent(player, PotionEffectType.JUMP_BOOST, 0);
    }

    private void equipArcher(Player player, int tier) {
        give(player, Material.BOW, 1, Material.ARROW, 16 + tier * 8, Material.WOODEN_SWORD, 1);
        setLeatherArmor(player);
    }

    private void equipBerserker(Player player, int tier) {
        give(player, Material.STONE_AXE, 1, Material.BREAD, tier + 1);
        setLeatherArmor(player);
    }

    private void equipChemist(Player player, int tier) {
        give(player, Material.STONE_SWORD, 1);
        player.getInventory().addItem(createPotion("Swiftness Tonic", PotionEffectType.SPEED, 240 + tier * 40));
        if (tier >= 2) player.getInventory().addItem(
                createPotion("Regeneration Tonic", PotionEffectType.REGENERATION, 120 + tier * 20));
        if (tier >= 3) player.getInventory().addItem(
                createPotion("Absorption Tonic", PotionEffectType.ABSORPTION, 240));
        setLeatherArmor(player);
    }

    private void equipAssassin(Player player, int tier) {
        give(player, Material.GOLDEN_SWORD, 1);
        if (tier >= 2) give(player, Material.ENDER_PEARL, 1);
        setLeatherArmor(player);
        permanent(player, PotionEffectType.SPEED, 0);
    }

    private void equipMiner(Player player, int tier) {
        give(player, tier >= 3 ? Material.IRON_PICKAXE : Material.STONE_PICKAXE, 1,
                Material.COBBLESTONE, 24 + tier * 8, Material.WOODEN_SWORD, 1);
        setLeatherArmor(player);
        permanent(player, PotionEffectType.HASTE, 0);
    }

    private void equipVampire(Player player, int tier) {
        player.getInventory().addItem(namedItem(Material.STONE_SWORD, ChatColor.RED + "Vampiric Blade"));
        setLeatherArmor(player);
        if (tier >= 3) player.getInventory().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
    }

    private void equipFrostMage(Player player, int tier) {
        give(player, Material.STONE_SWORD, 1);
        ItemStack wand = namedItem(Material.STICK, ChatColor.AQUA + "Frost Wand");
        ItemMeta meta = wand.getItemMeta();
        meta.setLore(List.of(ChatColor.GRAY + "Right-click: temporary ice bridge",
                ChatColor.GRAY + "and a short enemy slow."));
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand, new ItemStack(Material.SNOWBALL, 4 + tier * 4));
        setLeatherArmor(player);
    }

    private void equipJuggernaut(Player player, int tier) {
        give(player, Material.STONE_AXE, 1);
        setLeatherArmor(player);
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        player.getInventory().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
        if (tier >= 2) player.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        if (tier >= 3) player.getInventory().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
        permanent(player, PotionEffectType.SLOWNESS, tier >= 3 ? 1 : 0);
    }

    private void equipTrapper(Player player, int tier) {
        give(player, Material.STONE_SWORD, 1, Material.COBWEB, tier + 1,
                Material.TRIPWIRE_HOOK, tier + 2, Material.STRING, 4 + tier * 2);
        setLeatherArmor(player);
    }

    private void equipAlchemist(Player player, int tier) {
        give(player, Material.WOODEN_SWORD, 1, Material.BREAD, tier);
        player.getInventory().addItem(namedItem(Material.BREWING_STAND, ChatColor.LIGHT_PURPLE + "Field Brewery"));
        setLeatherArmor(player);
    }

    private void equipKnockbackWarrior(Player player, int tier) {
        ItemStack stick = namedItem(Material.STICK, "The Big Stick " + roman(tier));
        stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
        player.getInventory().addItem(stick, new ItemStack(Material.WOODEN_SWORD),
                new ItemStack(Material.SNOWBALL, tier * 4));
        setLeatherArmor(player);
        if (tier >= 3) player.getInventory().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
    }

    private void equipMobility(Player player, int tier) {
        give(player, Material.WOODEN_SWORD, 1, Material.FEATHER, tier + 2);
        setLeatherArmor(player);
        permanent(player, PotionEffectType.SPEED, 0);
        if (tier >= 3) permanent(player, PotionEffectType.JUMP_BOOST, 0);
    }

    private void give(Player player, Object... materialAmountPairs) {
        for (int index = 0; index < materialAmountPairs.length; index += 2) {
            player.getInventory().addItem(new ItemStack((Material) materialAmountPairs[index],
                    (Integer) materialAmountPairs[index + 1]));
        }
    }

    private void setLeatherArmor(Player player) {
        player.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
    }

    private void permanent(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPotion(String name, PotionEffectType type, int duration) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + name);
        meta.addCustomEffect(new PotionEffect(type, duration, 0), true);
        potion.setItemMeta(meta);
        return potion;
    }

    private String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(value);
        };
    }
}
