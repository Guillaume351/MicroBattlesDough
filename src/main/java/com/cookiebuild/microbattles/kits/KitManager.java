package com.cookiebuild.microbattles.kits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.cookiedough.utils.LocaleManager;

public class KitManager {

    private static KitManager instance;
    private final Map<String, Kit> originalKits = new HashMap<>();
    private final Map<String, TieredKit> tieredKits = new HashMap<>();
    private final Map<UUID, String> playerSelectedKits = new HashMap<>(); // "kitName:level"

    private KitManager() {
        initializeOriginalKits();
        initializeTieredKits();
    }

    public static synchronized KitManager getInstance() {
        if (instance == null) {
            instance = new KitManager();
        }
        return instance;
    }

    private void initializeOriginalKits() {
        // This is where the definitions from the old KitManager go.
        createDefaultKit();
        createExplosiveArcherKit();
        createEndermanKit();
        createKnockbackWarriorKit();
        createTankKit();
        createNinjaKit();
        createArcherKit();
        createBerserkerKit();
        createChemistKit();
        createAssassinKit();
        createMinerKit();
        createVampireKit();
        createFrostMageKit();
        createJuggernautKit();
        createTrapperKit();
        createAlchemistKit();
        createMobilityKit();
    }

    private void initializeTieredKits() {
        for (Kit originalKit : originalKits.values()) {
            if (originalKit.getName().equals("Default")) {
                continue; // Skip Default kit
            }

            TieredKit tieredKit = new TieredKit(originalKit.getName());

            int level1Price = originalKit.getPrice() / 3;
            int level1RequiredLevel = Math.max(1, originalKit.getRequiredLevel() / 3);
            boolean level1Free = (originalKit.getName().equals("Knockback Warrior")
                    || originalKit.getName().equals("Archer"));
            if (level1Free)
                level1Price = 0;

            int level2Price = (int) (originalKit.getPrice() * 0.7);
            int level2RequiredLevel = Math.max(level1RequiredLevel + 2, (originalKit.getRequiredLevel() * 2) / 3);

            int level3Price = originalKit.getPrice();
            int level3RequiredLevel = originalKit.getRequiredLevel();

            tieredKit.addLevel(1, level1Price, level1RequiredLevel, level1Free);
            tieredKit.addLevel(2, level2Price, level2RequiredLevel, false);
            tieredKit.addLevel(3, level3Price, level3RequiredLevel, false);

            tieredKits.put(originalKit.getName(), tieredKit);
        }
    }

    // Public API for Kits
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
        kits.removeIf(kit -> kit.getName().equals("Default")); // Don't give default randomly
        if (kits.isEmpty()) {
            return getOriginalKit("Default"); // Fallback
        }
        return kits.get(new Random().nextInt(kits.size()));
    }

    public void selectKit(UUID playerId, String kitName, int level) {
        playerSelectedKits.put(playerId, kitName + ":" + level);
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

    public String getKitDescription(String kitName, Player player) {
        Kit kit = getOriginalKit(kitName);
        if (kit == null) {
            return "Unknown kit.";
        }
        return LocaleManager.getMessage(kit.getDescription(), player.locale());
    }

    public boolean isKitLevelUnlocked(MinigameStatsService statsService, UUID playerId, String kitName, int level) {
        TieredKit tieredKit = getTieredKit(kitName);
        if (tieredKit == null)
            return false;
        KitLevel kitLevel = tieredKit.getLevel(level);
        if (kitLevel == null)
            return false;
        if (kitLevel.isDefaultUnlocked())
            return true;
        String tieredKitKey = kitName + ":L" + level;
        return statsService.hasUnlockedKit(playerId, MinigameStatsService.MICROBATTLES, tieredKitKey);
    }

    public boolean canAffordKitLevel(MinigameStatsService statsService, UUID playerId, String kitName, int level) {
        KitLevel kitLevel = getTieredKit(kitName).getLevel(level);
        if (kitLevel == null)
            return false;
        return statsService.getCoins(playerId, MinigameStatsService.MICROBATTLES) >= kitLevel.getPrice();
    }

    public boolean hasRequiredPlayerLevelForKit(MinigameStatsService statsService, UUID playerId, String kitName,
            int level) {
        KitLevel kitLevel = getTieredKit(kitName).getLevel(level);
        if (kitLevel == null)
            return false;
        return statsService.getLevel(playerId, MinigameStatsService.MICROBATTLES) >= kitLevel.getRequiredLevel();
    }

    public boolean purchaseKitLevel(MinigameStatsService statsService, UUID playerId, String kitName, int level) {
        if (!canAffordKitLevel(statsService, playerId, kitName, level)
                || !hasRequiredPlayerLevelForKit(statsService, playerId, kitName, level)) {
            return false;
        }
        KitLevel kitLevel = getTieredKit(kitName).getLevel(level);
        if (statsService.purchase(playerId, MinigameStatsService.MICROBATTLES, kitLevel.getPrice())) {
            statsService.unlockKit(playerId, MinigameStatsService.MICROBATTLES, kitName + ":L" + level);
            return true;
        }
        return false;
    }

    public void equipSelectedKit(Player player) {
        String selectedKit = playerSelectedKits.get(player.getUniqueId());
        if (selectedKit == null) {
            // Equip default kit if nothing is selected
            getOriginalKit("Default").equipPlayer(player);
            return;
        }

        String[] parts = selectedKit.split(":");
        if (parts.length != 2)
            return;

        String kitName = parts[0];
        int level = Integer.parseInt(parts[1]);

        equipTieredKit(player, kitName, level);
    }

    // Kit Equipping Logic
    private void equipTieredKit(Player player, String kitName, int level) {
        player.getInventory().clear();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        switch (kitName) {
            case "Explosive Archer":
                equipExplosiveArcher(player, level);
                break;
            case "Enderman":
                equipEnderman(player, level);
                break;
            case "Tank":
                equipTank(player, level);
                break;
            case "Ninja":
                equipNinja(player, level);
                break;
            case "Archer":
                equipArcher(player, level);
                break;
            case "Berserker":
                equipBerserker(player, level);
                break;
            case "Chemist":
                equipChemist(player, level);
                break;
            case "Assassin":
                equipAssassin(player, level);
                break;
            case "Miner":
                equipMiner(player, level);
                break;
            case "Vampire":
                equipVampire(player, level);
                break;
            case "Frost Mage":
                equipFrostMage(player, level);
                break;
            case "Juggernaut":
                equipJuggernaut(player, level);
                break;
            case "Trapper":
                equipTrapper(player, level);
                break;
            case "Alchemist":
                equipAlchemist(player, level);
                break;
            case "Knockback Warrior":
                equipKnockbackWarrior(player, level);
                break;
            case "Mobility":
                equipMobility(player, level);
                break;
            default:
                Kit originalKit = getOriginalKit(kitName);
                if (originalKit != null) {
                    originalKit.equipPlayer(player);
                }
                break;
        }
    }

    private void equipExplosiveArcher(Player player, int level) {
        ItemStack bow = new ItemStack(Material.BOW);
        if (level >= 2)
            bow.addEnchantment(Enchantment.POWER, 1);
        if (level >= 3)
            bow.addEnchantment(Enchantment.FLAME, 1);
        player.getInventory().setItem(0, bow);
        player.getInventory().setItem(1, new ItemStack(Material.ARROW, 16 + (level * 8)));
        player.getInventory().setItem(2, new ItemStack(Material.TNT, level * 2));
        if (level >= 2)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
    }

    private void equipEnderman(Player player, int level) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        if (level >= 3)
            sword.addEnchantment(Enchantment.SHARPNESS, 1);
        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(1, new ItemStack(Material.ENDER_PEARL, 2 + level));
        if (level >= 2)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0));
    }

    private void equipTank(Player player, int level) {
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        if (level >= 2)
            sword = new ItemStack(Material.IRON_SWORD);
        if (level >= 3)
            sword.addEnchantment(Enchantment.SHARPNESS, 1);
        player.getInventory().setItem(0, sword);
        if (level >= 1)
            player.getInventory().setItem(8, new ItemStack(Material.SHIELD));
        Material armorMaterial = level == 1 ? Material.LEATHER_CHESTPLATE
                : level == 2 ? Material.CHAINMAIL_CHESTPLATE : Material.IRON_CHESTPLATE;
        player.getInventory().setChestplate(new ItemStack(armorMaterial));
        if (level >= 2)
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0));
    }

    private void equipNinja(Player player, int level) {
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        if (level >= 2)
            sword = new ItemStack(Material.IRON_SWORD);
        if (level >= 3)
            sword.addEnchantment(Enchantment.SHARPNESS, 1);
        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(1, new ItemStack(Material.SNOWBALL, 6 + (level * 2)));
        int speedLevel = Math.min(level - 1, 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speedLevel));
        if (level >= 2)
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 0));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1));
    }

    private void equipArcher(Player player, int level) {
        ItemStack bow = new ItemStack(Material.BOW);
        if (level >= 2)
            bow.addEnchantment(Enchantment.POWER, 1);
        if (level >= 3)
            bow.addEnchantment(Enchantment.INFINITY, 1);
        player.getInventory().setItem(0, bow);
        int arrowCount = level >= 3 ? 1 : 32 + (level * 16);
        player.getInventory().setItem(1, new ItemStack(Material.ARROW, arrowCount));
        if (level >= 2)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
    }

    private void equipBerserker(Player player, int level) {
        ItemStack axe = new ItemStack(Material.STONE_AXE);
        if (level >= 2)
            axe = new ItemStack(Material.IRON_AXE);
        if (level >= 3) {
            axe = new ItemStack(Material.IRON_AXE);
            axe.addEnchantment(Enchantment.SHARPNESS, 1);
        }
        player.getInventory().setItem(0, axe);
        int strengthLevel = Math.min(level - 1, 1);
        if (level >= 1)
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, strengthLevel));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
    }

    private void equipChemist(Player player, int level) {
        player.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.SPLASH_POTION, level * 2));
        if (level >= 2)
            player.getInventory().setItem(2, new ItemStack(Material.LINGERING_POTION, level));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
    }

    private void equipAssassin(Player player, int level) {
        ItemStack sword = new ItemStack(Material.GOLDEN_SWORD);
        if (level >= 2)
            sword.addEnchantment(Enchantment.SHARPNESS, 1);
        if (level >= 3)
            sword.addEnchantment(Enchantment.SHARPNESS, 2);
        player.getInventory().setItem(0, sword);
        int speedLevel = level - 1;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speedLevel));
        if (level >= 2)
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 0));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0));
    }

    private void equipMiner(Player player, int level) {
        ItemStack pickaxe = new ItemStack(Material.STONE_PICKAXE);
        if (level >= 2)
            pickaxe = new ItemStack(Material.IRON_PICKAXE);
        if (level >= 3) {
            pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
            pickaxe.addEnchantment(Enchantment.EFFICIENCY, 1);
        }
        player.getInventory().setItem(0, pickaxe);
        player.getInventory().setItem(1, new ItemStack(Material.COBBLESTONE, 16 + (level * 16)));
        if (level >= 2)
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, 0));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));
    }

    private void equipVampire(Player player, int level) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        if (level >= 2)
            sword.addEnchantment(Enchantment.SHARPNESS, 1);
        player.getInventory().setItem(0, sword);
        int lifeStealChance = 10 + (level * 10); // 20%, 30%, 40%
        // Logic for lifesteal would be in KitEffectListener
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0));
    }

    private void equipFrostMage(Player player, int level) {
        player.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.SNOWBALL, 16 + (level * 16)));
        if (level >= 2)
            player.getInventory().setItem(2, new ItemStack(Material.ICE, level * 8));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
    }

    private void equipJuggernaut(Player player, int level) {
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        if (level >= 2)
            axe = new ItemStack(Material.DIAMOND_AXE);
        if (level >= 3) {
            axe = new ItemStack(Material.DIAMOND_AXE);
            axe.addEnchantment(Enchantment.SHARPNESS, 1);
        }
        player.getInventory().setItem(0, axe);
        if (level >= 1)
            player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        if (level >= 2)
            player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        if (level >= 3) {
            player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        }
        if (level >= 1)
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
    }

    private void equipTrapper(Player player, int level) {
        player.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.TRIPWIRE_HOOK, 4 + (level * 4)));
        player.getInventory().setItem(2, new ItemStack(Material.STRING, 8 + (level * 8)));
        if (level >= 2)
            player.getInventory().setItem(3, new ItemStack(Material.REDSTONE, level * 8));
        if (level >= 3)
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0));
    }

    private void equipAlchemist(Player player, int level) {
        player.getInventory().setItem(0, new ItemStack(Material.STONE_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.SPLASH_POTION, level * 2));
        if (level >= 2)
            player.getInventory().setItem(2, new ItemStack(Material.LINGERING_POTION, level));
        if (level >= 3) {
            player.getInventory().setItem(3, new ItemStack(Material.BREWING_STAND));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0));
        }
    }

    private void equipKnockbackWarrior(Player player, int level) {
        ItemStack kbStick = new ItemStack(Material.STICK);
        int knockbackLevel = Math.min(2 + level, 4);
        kbStick.addUnsafeEnchantment(Enchantment.KNOCKBACK, knockbackLevel);
        ItemMeta meta = kbStick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + "The Big Stick " + getRomanNumeral(level));
            kbStick.setItemMeta(meta);
        }
        player.getInventory().setItem(0, kbStick);
        if (level >= 1) {
            player.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            player.getInventory().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
            player.getInventory().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
        }
        if (level >= 2) {
            player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
            player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        }
        if (level >= 3) {
            player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
            player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        }
    }

    private void equipMobility(Player player, int level) {
        player.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        player.getInventory().setItem(1, new ItemStack(Material.FEATHER, 2 + level));
        player.getInventory().setItem(2, new ItemStack(Material.SUGAR, 1 + level));
        player.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        int speedLevel = Math.min(level - 1, 1);
        int jumpLevel = Math.min(level - 1, 1);
        if (level >= 1)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speedLevel));
        if (level >= 2)
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, jumpLevel));
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1));
        }
    }

    private String getRomanNumeral(int number) {
        switch (number) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            default:
                return String.valueOf(number);
        }
    }

    // Original Kit Definitions
    private void addOriginalKit(Kit kit) {
        originalKits.put(kit.getName(), kit);
    }

    private void createDefaultKit() {
        Kit kit = new Kit("Default", 0, 0, true, "kit.default.description");
        kit.addItem(Material.STONE_SWORD, 1);
        kit.addItem(Material.BOW, 1);
        kit.addItem(Material.GOLDEN_PICKAXE, 1);
        kit.addItem(Material.ARROW, 24);
        kit.addItem(Material.BREAD, 5);
        kit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        addOriginalKit(kit);
    }

    private void createExplosiveArcherKit() {
        Kit kit = new Kit("Explosive Archer", 120, 7, false, "kit.explosive_archer.description");
        kit.addItem(Material.BOW, 1, Enchantment.INFINITY, 1);
        kit.addItem(Material.ARROW, 1);
        kit.addItem(Material.TNT, 8);
        kit.setArmor(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        addOriginalKit(kit);
    }

    private void createEndermanKit() {
        Kit kit = new Kit("Enderman", 250, 16, false, "kit.enderman.description");
        kit.addItem(Material.ENDER_PEARL, 12);
        kit.addItem(Material.IRON_SWORD, 1);
        kit.setArmor(Material.IRON_HELMET, Material.LEATHER_CHESTPLATE, Material.IRON_LEGGINGS, Material.LEATHER_BOOTS);
        addOriginalKit(kit);
    }

    private void createKnockbackWarriorKit() {
        Kit kit = new Kit("Knockback Warrior", 50, 0, true, "kit.knockback_warrior.description");
        ItemStack kbStick = new ItemStack(Material.STICK);
        kbStick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 5);
        ItemMeta meta = kbStick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + "The Big Stick");
            kbStick.setItemMeta(meta);
        }
        kit.addItemStack(kbStick, 1);
        kit.setArmor(Material.CHAINMAIL_HELMET, Material.IRON_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
                Material.CHAINMAIL_BOOTS);
        addOriginalKit(kit);
    }

    private void createTankKit() {
        Kit kit = new Kit("Tank", 230, 15, false, "kit.tank.description");
        kit.addItem(Material.DIAMOND_SWORD, 1);
        kit.addItem(Material.SHIELD, 1);
        kit.addItem(Material.GOLDEN_APPLE, 3);
        kit.setArmor(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS,
                Material.DIAMOND_BOOTS);
        addOriginalKit(kit);
    }

    private void createNinjaKit() {
        Kit kit = new Kit("Ninja", 300, 22, false, "kit.ninja.description");
        kit.addItem(Material.IRON_SWORD, 1, Enchantment.SHARPNESS, 1);
        kit.addItem(Material.ENDER_PEARL, 5);
        kit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        addOriginalKit(kit);
    }

    private void createArcherKit() {
        Kit kit = new Kit("Archer", 0, 0, true, "kit.archer.description");
        kit.addItem(Material.BOW, 1, Enchantment.POWER, 2);
        kit.addItem(Material.ARROW, 32);
        kit.addItem(Material.WOODEN_SWORD, 1);
        kit.setArmor(Material.LEATHER_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        addOriginalKit(kit);
    }

    private void createBerserkerKit() {
        Kit kit = new Kit("Berserker", 150, 8, false, "kit.berserker.description");
        kit.addItem(Material.DIAMOND_AXE, 1, Enchantment.SHARPNESS, 2);
        kit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        addOriginalKit(kit);
    }

    private void createChemistKit() {
        Kit kit = new Kit("Chemist", 280, 20, false, "kit.chemist.description");
        kit.addItem(Material.STONE_SWORD, 1);
        kit.setArmor(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
                Material.CHAINMAIL_BOOTS);
        addOriginalKit(kit);
    }

    private void createAssassinKit() {
        Kit kit = new Kit("Assassin", 350, 28, false, "kit.assassin.description");
        ItemStack dagger = new ItemStack(Material.GOLDEN_SWORD);
        dagger.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
        ItemMeta daggerMeta = dagger.getItemMeta();
        if (daggerMeta != null) {
            daggerMeta.setDisplayName(ChatColor.RESET + "Assassin's Dagger");
            dagger.setItemMeta(daggerMeta);
        }
        kit.addItemStack(dagger, 1);
        kit.addItem(Material.ENDER_PEARL, 3);
        kit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        addOriginalKit(kit);
    }

    private void createMinerKit() {
        Kit kit = new Kit("Miner", 80, 3, false, "kit.miner.description");
        kit.addItem(Material.DIAMOND_PICKAXE, 1, Enchantment.EFFICIENCY, 2);
        kit.addItem(Material.OAK_WOOD, 32);
        kit.addItem(Material.COBBLESTONE, 64);
        kit.setArmor(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
        addOriginalKit(kit);
    }

    private void createVampireKit() {
        Kit kit = new Kit("Vampire", 220, 14, false, "kit.vampire.description");
        ItemStack vampireSword = new ItemStack(Material.IRON_SWORD);
        ItemMeta vampireMeta = vampireSword.getItemMeta();
        if (vampireMeta != null) {
            vampireMeta.setDisplayName(ChatColor.RESET + "" + ChatColor.RED + "Vampiric Blade");
            vampireSword.setItemMeta(vampireMeta);
        }
        kit.addItemStack(vampireSword, 1);
        kit.setArmor(Material.LEATHER_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        addOriginalKit(kit);
    }

    private void createFrostMageKit() {
        Kit kit = new Kit("Frost Mage", 320, 25, false, "kit.frost_mage.description");
        ItemStack iceWand = new ItemStack(Material.STICK);
        iceWand.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
        ItemMeta wandMeta = iceWand.getItemMeta();
        if (wandMeta != null) {
            wandMeta.setDisplayName(ChatColor.AQUA + "Frost Wand");
            wandMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Right-click to shoot a slowing snowball."));
            iceWand.setItemMeta(wandMeta);
        }
        kit.addItemStack(iceWand, 1);
        kit.addItem(Material.SNOWBALL, 32);
        kit.setArmor(Material.CHAINMAIL_HELMET, Material.IRON_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
                Material.IRON_BOOTS);
        addOriginalKit(kit);
    }

    private void createJuggernautKit() {
        Kit kit = new Kit("Juggernaut", 400, 30, false, "kit.juggernaut.description");
        kit.addItem(Material.NETHERITE_AXE, 1, Enchantment.SHARPNESS, 1);
        kit.addItem(Material.SHIELD, 1);
        kit.addItem(Material.ENCHANTED_GOLDEN_APPLE, 1);
        kit.setArmor(Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_BOOTS);
        addOriginalKit(kit);
    }

    private void createTrapperKit() {
        Kit kit = new Kit("Trapper", 100, 5, false, "kit.trapper.description");
        kit.addItem(Material.STONE_SWORD, 1);
        kit.addItem(Material.TRIPWIRE_HOOK, 12);
        kit.addItem(Material.STRING, 48);
        kit.addItem(Material.TNT, 4);
        kit.setArmor(Material.LEATHER_HELMET, Material.IRON_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.IRON_BOOTS);
        addOriginalKit(kit);
    }

    private void createAlchemistKit() {
        Kit kit = new Kit("Alchemist", 200, 12, false, "kit.alchemist.description");
        kit.addItem(Material.IRON_SWORD, 1);
        kit.addItem(Material.BREWING_STAND, 1);
        kit.setArmor(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS,
                Material.GOLDEN_BOOTS);
        addOriginalKit(kit);
    }

    private void createMobilityKit() {
        Kit kit = new Kit("Mobility", 90, 4, false, "kit.mobility.description");
        kit.addItem(Material.IRON_SWORD, 1);
        kit.addItem(Material.FEATHER, 3);
        kit.addItem(Material.SUGAR, 2);
        kit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        addOriginalKit(kit);
    }
}