package com.cookiebuild.microbattles.kits;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.cookiedough.utils.PotionUtil;

public class KitManager {
    private static final KitManager instance = new KitManager();
    List<Kit> kits = new ArrayList<>();

    private KitManager() {
        // register kits
        createDefaultKit();
        createExplosiveArcherKit();
        createEndermanKit();
        createKnockbackWarriorKit();
        createTankKit();
        createNinjaKit();
        // createPyromancerKit(); // Supprimé

        // New kit creation methods
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

    public static KitManager getInstance() {
        return instance;
    }

    private void createDefaultKit() {
        Kit defaultKit = new Kit("Default", 0, 0, true, "kit.default.description");
        defaultKit.addItem(Material.STONE_SWORD, 1);
        defaultKit.addItem(Material.BOW, 1);
        defaultKit.addItem(Material.GOLDEN_PICKAXE, 1);
        defaultKit.addItem(Material.ARROW, 24);
        defaultKit.addItem(Material.BREAD, 5);
        defaultKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        kits.add(defaultKit);
    }

    private void createExplosiveArcherKit() {
        Kit explosiveArcherKit = new Kit("Explosive Archer", 120, 7, false, "kit.explosive_archer.description");
        explosiveArcherKit.addItem(Material.BOW, 1, Enchantment.INFINITY, 1);
        explosiveArcherKit.addItem(Material.ARROW, 1);
        explosiveArcherKit.addItem(Material.TNT, 8);
        explosiveArcherKit.setArmor(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        kits.add(explosiveArcherKit);
    }

    private void createEndermanKit() {
        Kit endermanKit = new Kit("Enderman", 250, 16, false, "kit.enderman.description");
        endermanKit.addItem(Material.ENDER_PEARL, 12);
        endermanKit.addItem(Material.IRON_SWORD, 1);
        endermanKit.addItem(Material.CHORUS_FRUIT, 3);
        endermanKit.setArmor(Material.IRON_HELMET, Material.LEATHER_CHESTPLATE, Material.IRON_LEGGINGS,
                Material.LEATHER_BOOTS);
        kits.add(endermanKit);
    }

    private void createKnockbackWarriorKit() {
        Kit knockbackWarriorKit = new Kit("Knockback Warrior", 50, 0, true, "kit.knockback_warrior.description");
        ItemStack kbStick = new ItemStack(Material.STICK);
        kbStick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 5);
        ItemMeta meta = kbStick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + "The Big Stick");
            kbStick.setItemMeta(meta);
        }
        knockbackWarriorKit.addItemStack(kbStick, 1);
        knockbackWarriorKit.setArmor(Material.CHAINMAIL_HELMET, Material.IRON_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
                Material.CHAINMAIL_BOOTS);
        kits.add(knockbackWarriorKit);
    }

    private void createTankKit() {
        Kit tankKit = new Kit("Tank", 230, 15, false, "kit.tank.description");
        tankKit.addItem(Material.DIAMOND_SWORD, 1);
        tankKit.addItem(Material.SHIELD, 1);
        tankKit.addItem(Material.GOLDEN_APPLE, 3);
        tankKit.addItemStack(PotionUtil.createPotion(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0), 1);
        tankKit.addItemStack(PotionUtil.createPotion(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0), 1);
        tankKit.setArmor(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS,
                Material.DIAMOND_BOOTS);
        kits.add(tankKit);
    }

    private void createNinjaKit() {
        Kit ninjaKit = new Kit("Ninja", 300, 22, false, "kit.ninja.description");
        ninjaKit.addItem(Material.IRON_SWORD, 1, Enchantment.SHARPNESS, 1);
        ninjaKit.addItem(Material.ENDER_PEARL, 5);
        ninjaKit.addItemStack(PotionUtil.createPotion(PotionEffectType.SPEED, 1800, 1), 1);
        ninjaKit.addItemStack(PotionUtil.createPotion(PotionEffectType.INVISIBILITY, 600, 0), 1);
        ninjaKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        kits.add(ninjaKit);
    }

    // createPyromancerKit() method fully removed

    private void createArcherKit() {
        Kit archerKit = new Kit("Archer", 0, 0, true, "kit.archer.description");
        archerKit.addItem(Material.BOW, 1, Enchantment.POWER, 2);
        archerKit.addItem(Material.ARROW, 32);
        archerKit.addItem(Material.WOODEN_SWORD, 1);
        archerKit.setArmor(Material.LEATHER_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        kits.add(archerKit);
    }

    private void createBerserkerKit() {
        Kit berserkerKit = new Kit("Berserker", 150, 8, false, "kit.berserker.description");
        berserkerKit.addItem(Material.DIAMOND_AXE, 1, Enchantment.SHARPNESS, 2);
        berserkerKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        kits.add(berserkerKit);
    }

    private void createChemistKit() {
        Kit chemistKit = new Kit("Chemist", 280, 20, false, "kit.chemist.description");
        chemistKit.addItemStack(PotionUtil.createSplashPotion(PotionEffectType.INSTANT_DAMAGE, 0, 0), 3);
        chemistKit.addItemStack(PotionUtil.createSplashPotion(PotionEffectType.POISON, 300, 0), 2);
        chemistKit.addItemStack(PotionUtil.createSplashPotion(PotionEffectType.SLOWNESS, 300, 1), 2);
        chemistKit.addItem(Material.STONE_SWORD, 1);
        chemistKit.setArmor(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
                Material.CHAINMAIL_BOOTS);
        kits.add(chemistKit);
    }

    private void createAssassinKit() {
        Kit assassinKit = new Kit("Assassin", 350, 28, false, "kit.assassin.description");
        ItemStack dagger = new ItemStack(Material.GOLDEN_SWORD);
        dagger.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
        ItemMeta daggerMeta = dagger.getItemMeta();
        if (daggerMeta != null) {
            daggerMeta.setDisplayName(ChatColor.RESET + "Assassin's Dagger");
            dagger.setItemMeta(daggerMeta);
        }
        assassinKit.addItemStack(dagger, 1);
        assassinKit.addItem(Material.ENDER_PEARL, 3);
        assassinKit.addItemStack(PotionUtil.createPotion(PotionEffectType.INVISIBILITY, 900, 0), 1);
        // Assuming BLACK_LEATHER_... are custom or you handle dyeing elsewhere.
        // For simplicity, using standard leather if specific dyed items aren't
        // straightforward.
        assassinKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        kits.add(assassinKit);
    }

    private void createMinerKit() {
        Kit minerKit = new Kit("Miner", 80, 3, false, "kit.miner.description");
        minerKit.addItem(Material.DIAMOND_PICKAXE, 1, Enchantment.EFFICIENCY, 2);
        minerKit.addItem(Material.OAK_WOOD, 32);
        minerKit.addItem(Material.COBBLESTONE, 64);
        minerKit.addItem(Material.TNT, 2);
        minerKit.setArmor(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
        kits.add(minerKit);
    }

    private void createVampireKit() {
        Kit vampireKit = new Kit("Vampire", 220, 14, false, "kit.vampire.description");
        ItemStack vampireSword = new ItemStack(Material.IRON_SWORD);
        ItemMeta vampireMeta = vampireSword.getItemMeta();
        if (vampireMeta != null) {
            vampireMeta.setDisplayName(ChatColor.RESET + "" + ChatColor.RED + "Vampiric Blade");
            vampireSword.setItemMeta(vampireMeta);
        }
        vampireKit.addItemStack(vampireSword, 1);
        vampireKit.setArmor(Material.LEATHER_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        kits.add(vampireKit);
    }

    private void createFrostMageKit() {
        Kit frostMageKit = new Kit("Frost Mage", 320, 25, false, "kit.frost_mage.description");
        ItemStack iceWand = new ItemStack(Material.STICK);
        iceWand.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
        ItemMeta wandMeta = iceWand.getItemMeta();
        if (wandMeta != null) {
            wandMeta.setDisplayName(ChatColor.AQUA + "Frost Wand");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right-click to shoot a slowing snowball.");
            wandMeta.setLore(lore);
            iceWand.setItemMeta(wandMeta);
        }
        frostMageKit.addItemStack(iceWand, 1);
        frostMageKit.addItem(Material.SNOWBALL, 32);
        frostMageKit.addItem(Material.ICE, 16);
        frostMageKit.addItemStack(PotionUtil.createPotion(PotionEffectType.SPEED, Integer.MAX_VALUE, 0), 1);
        frostMageKit.setArmor(Material.CHAINMAIL_HELMET, Material.IRON_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
                Material.IRON_BOOTS);
        kits.add(frostMageKit);
    }

    private void createJuggernautKit() {
        Kit juggernautKit = new Kit("Juggernaut", 400, 30, false, "kit.juggernaut.description");
        juggernautKit.addItem(Material.NETHERITE_AXE, 1, Enchantment.SHARPNESS, 1);
        juggernautKit.addItem(Material.SHIELD, 1);
        juggernautKit.addItem(Material.ENCHANTED_GOLDEN_APPLE, 1);
        juggernautKit.addItemStack(PotionUtil.createPotion(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 1), 1);
        juggernautKit.addItemStack(PotionUtil.createPotion(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1),
                1);
        juggernautKit.setArmor(Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_BOOTS);
        kits.add(juggernautKit);
    }

    private void createTrapperKit() {
        Kit trapperKit = new Kit("Trapper", 100, 5, false, "kit.trapper.description");
        trapperKit.addItem(Material.STONE_SWORD, 1);
        trapperKit.addItem(Material.TRIPWIRE_HOOK, 12);
        trapperKit.addItem(Material.STRING, 48);
        trapperKit.addItem(Material.OAK_PRESSURE_PLATE, 10);
        trapperKit.addItem(Material.TNT, 4);
        trapperKit.addItem(Material.COBWEB, 8);
        trapperKit.setArmor(Material.LEATHER_HELMET, Material.IRON_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.IRON_BOOTS);
        kits.add(trapperKit);
    }

    private void createAlchemistKit() {
        Kit alchemistKit = new Kit("Alchemist", 200, 12, false, "kit.alchemist.description");
        alchemistKit.addItem(Material.IRON_SWORD, 1);
        alchemistKit.addItem(Material.BREWING_STAND, 1);
        alchemistKit.addItem(Material.NETHER_WART, 5);
        alchemistKit.addItem(Material.GLASS_BOTTLE, 6);
        alchemistKit.addItem(Material.BLAZE_POWDER, 2);
        alchemistKit.addItem(Material.GHAST_TEAR, 2);
        alchemistKit.addItem(Material.SPIDER_EYE, 2);
        alchemistKit.addItem(Material.SUGAR, 2);
        alchemistKit.addItem(Material.GUNPOWDER, 3);
        alchemistKit.setArmor(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS,
                Material.GOLDEN_BOOTS);
        kits.add(alchemistKit);
    }

    private void createMobilityKit() {
        Kit mobilityKit = new Kit("Mobility", 90, 4, false, "kit.mobility.description");
        mobilityKit.addItem(Material.IRON_SWORD, 1);
        mobilityKit.addItem(Material.FEATHER, 3);
        mobilityKit.addItem(Material.SUGAR, 2);
        mobilityKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        kits.add(mobilityKit);
    }

    public Kit getKit(String name) {
        for (Kit kit : kits) {
            if (kit.getName().equals(name)) {
                return kit;
            }
        }
        return null;
    }

    public List<Kit> getAllKits() {
        return new ArrayList<>(kits);
    }

    public List<Kit> getAvailableKits(MinigameStatsService statsService, java.util.UUID playerId) {
        return kits.stream()
                .filter(kit -> isKitUnlocked(statsService, playerId, kit)
                        && hasRequiredLevelForKit(statsService, playerId, kit))
                .collect(Collectors.toList());
    }

    public boolean isKitUnlocked(MinigameStatsService statsService, java.util.UUID playerId, Kit kit) {
        return kit.isDefaultUnlocked()
                || statsService.hasUnlockedKit(playerId, MinigameStatsService.MICROBATTLES, kit.getName());
    }

    public boolean canAffordKit(MinigameStatsService statsService, java.util.UUID playerId, Kit kit) {
        return statsService.getCoins(playerId, MinigameStatsService.MICROBATTLES) >= kit.getPrice();
    }

    public boolean hasRequiredLevelForKit(MinigameStatsService statsService, java.util.UUID playerId, Kit kit) {
        return statsService.getLevel(playerId, MinigameStatsService.MICROBATTLES) >= kit.getRequiredLevel();
    }

    public PurchaseResult purchaseKit(MinigameStatsService statsService, java.util.UUID playerId, Kit kit) {
        if (isKitUnlocked(statsService, playerId, kit)) {
            return PurchaseResult.ALREADY_UNLOCKED;
        }
        if (!hasRequiredLevelForKit(statsService, playerId, kit)) {
            return PurchaseResult.LEVEL_TOO_LOW;
        }
        if (!canAffordKit(statsService, playerId, kit)) {
            return PurchaseResult.NOT_ENOUGH_COINS;
        }
        if (statsService.purchase(playerId, MinigameStatsService.MICROBATTLES, kit.getPrice())) {
            statsService.unlockKit(playerId, MinigameStatsService.MICROBATTLES, kit.getName());
            return PurchaseResult.SUCCESS;
        }
        return PurchaseResult.ERROR; // Ne devrait pas arriver si les vérifications précédentes sont correctes
    }

    // Potentiellement à déplacer dans la logique de jeu (ex: MicroBattlesGame)
    // Pour l'instant, on assume que le joueur a déjà le kit disponible.
    public boolean selectKit(MinigameStatsService statsService, java.util.UUID playerId, Kit kitToSelect) {
        if (isKitUnlocked(statsService, playerId, kitToSelect)
                && hasRequiredLevelForKit(statsService, playerId, kitToSelect)) {
            // Logique pour marquer le kit comme sélectionné pour le joueur pour la partie
            // en cours.
            // Cela pourrait être stocké dans une Map<UUID, Kit> dans MicroBattlesGame par
            // exemple.
            // playerSelectedKits.put(playerId, kitToSelect);
            return true;
        }
        return false;
    }

    public Kit getRandomKit() {
        Random random = new Random();
        if (kits.isEmpty()) {
            return null;
        }
        return kits.get(random.nextInt(kits.size()));
    }

    /**
     * Donne un cookie de sélection de kit au joueur au lieu d'équiper directement
     * un kit
     */
    public static void giveKitSelectorCookie(org.bukkit.entity.Player player) {
        // Importer la classe KitSelectorListener pour accéder à la méthode statique
        ItemStack cookie = com.cookiebuild.microbattles.listener.KitSelectorListener.createKitSelectorCookie();
        player.getInventory().addItem(cookie);
        player.sendMessage(
                ChatColor.YELLOW + "Utilisez le cookie pour sélectionner votre kit avant le début de la partie !");
    }

    // Énumération pour les résultats d'achat
    public enum PurchaseResult {
        SUCCESS,
        NOT_ENOUGH_COINS,
        LEVEL_TOO_LOW,
        ALREADY_UNLOCKED,
        ERROR
    }
}