package com.cookiebuild.microbattles.kits;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * Gestionnaire de kits à niveaux qui étend le système existant
 * Chaque kit original a maintenant 3 niveaux avec des prix et puissances
 * différents
 */
public class TieredKitManager {
    private static TieredKitManager instance;
    private final KitManager originalKitManager;
    private final Map<String, TieredKit> tieredKits;
    private final Map<UUID, String> playerSelectedTieredKits; // playerId -> "kitName:level"

    private TieredKitManager(KitManager originalKitManager) {
        this.originalKitManager = originalKitManager;
        this.tieredKits = new HashMap<>();
        this.playerSelectedTieredKits = new HashMap<>();
        initializeTieredKits();
    }

    public static TieredKitManager getInstance(KitManager originalKitManager) {
        if (instance == null) {
            instance = new TieredKitManager(originalKitManager);
        }
        return instance;
    }

    public static class TieredKit {
        private final String baseName;
        private final List<KitLevel> levels;

        public TieredKit(String baseName) {
            this.baseName = baseName;
            this.levels = new ArrayList<>();
        }

        public void addLevel(int level, int price, int requiredLevel, boolean defaultUnlocked) {
            levels.add(new KitLevel(level, price, requiredLevel, defaultUnlocked));
        }

        public String getBaseName() {
            return baseName;
        }

        public List<KitLevel> getLevels() {
            return levels;
        }

        public KitLevel getLevel(int level) {
            return levels.stream().filter(l -> l.getLevel() == level).findFirst().orElse(null);
        }
    }

    public static class KitLevel {
        private final int level;
        private final int price;
        private final int requiredLevel;
        private final boolean defaultUnlocked;

        public KitLevel(int level, int price, int requiredLevel, boolean defaultUnlocked) {
            this.level = level;
            this.price = price;
            this.requiredLevel = requiredLevel;
            this.defaultUnlocked = defaultUnlocked;
        }

        public int getLevel() {
            return level;
        }

        public int getPrice() {
            return price;
        }

        public int getRequiredLevel() {
            return requiredLevel;
        }

        public boolean isDefaultUnlocked() {
            return defaultUnlocked;
        }

        public String getDisplayName(String baseName) {
            return baseName + " " + getRomanNumeral(level);
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
    }

    private void initializeTieredKits() {
        // Récupérer tous les kits originaux et créer des versions à niveaux
        List<Kit> originalKits = originalKitManager.getAllKits();

        for (Kit originalKit : originalKits) {
            if (originalKit.getName().equals("Default")) {
                continue; // Skip Default kit - reste tel quel
            }

            TieredKit tieredKit = new TieredKit(originalKit.getName());

            // Niveau 1: Prix réduit, puissance réduite
            int level1Price = originalKit.getPrice() / 3;
            int level1RequiredLevel = Math.max(1, originalKit.getRequiredLevel() / 3);
            boolean level1Free = (originalKit.getName().equals("Knockback Warrior")
                    || originalKit.getName().equals("Archer"));
            if (level1Free)
                level1Price = 0;

            // Niveau 2: Prix intermédiaire, puissance intermédiaire
            int level2Price = (int) (originalKit.getPrice() * 0.7);
            int level2RequiredLevel = Math.max(level1RequiredLevel + 2, (originalKit.getRequiredLevel() * 2) / 3);

            // Niveau 3: Prix et puissance originaux
            int level3Price = originalKit.getPrice();
            int level3RequiredLevel = originalKit.getRequiredLevel();

            tieredKit.addLevel(1, level1Price, level1RequiredLevel, level1Free);
            tieredKit.addLevel(2, level2Price, level2RequiredLevel, false);
            tieredKit.addLevel(3, level3Price, level3RequiredLevel, false);

            tieredKits.put(originalKit.getName(), tieredKit);
        }
    }

    public List<TieredKit> getAllTieredKits() {
        return new ArrayList<>(tieredKits.values());
    }

    public TieredKit getTieredKit(String baseName) {
        return tieredKits.get(baseName);
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

        // Vérifier si le kit est débloqué dans la base de données
        String tieredKitKey = kitName + ":L" + level;
        return statsService.hasUnlockedKit(playerId, MinigameStatsService.MICROBATTLES, tieredKitKey);
    }

    public boolean canAffordKitLevel(MinigameStatsService statsService, UUID playerId, String kitName, int level) {
        TieredKit tieredKit = getTieredKit(kitName);
        if (tieredKit == null)
            return false;

        KitLevel kitLevel = tieredKit.getLevel(level);
        if (kitLevel == null)
            return false;

        int playerCoins = statsService.getCoins(playerId, MinigameStatsService.MICROBATTLES);
        return playerCoins >= kitLevel.getPrice();
    }

    public boolean hasRequiredLevelForKit(MinigameStatsService statsService, UUID playerId, String kitName, int level) {
        TieredKit tieredKit = getTieredKit(kitName);
        if (tieredKit == null)
            return false;

        KitLevel kitLevel = tieredKit.getLevel(level);
        if (kitLevel == null)
            return false;

        int playerLevel = statsService.getLevel(playerId, MinigameStatsService.MICROBATTLES);
        return playerLevel >= kitLevel.getRequiredLevel();
    }

    public boolean purchaseKitLevel(MinigameStatsService statsService, UUID playerId, String kitName, int level) {
        TieredKit tieredKit = getTieredKit(kitName);
        if (tieredKit == null)
            return false;

        KitLevel kitLevel = tieredKit.getLevel(level);
        if (kitLevel == null)
            return false;

        if (!canAffordKitLevel(statsService, playerId, kitName, level) ||
                !hasRequiredLevelForKit(statsService, playerId, kitName, level)) {
            return false;
        }

        // Déduire les pièces et débloquer le kit
        if (statsService.purchase(playerId, MinigameStatsService.MICROBATTLES, kitLevel.getPrice())) {
            String tieredKitKey = kitName + ":L" + level;
            statsService.unlockKit(playerId, MinigameStatsService.MICROBATTLES, tieredKitKey);
            return true;
        }

        return false;
    }

    public void selectKitLevel(UUID playerId, String kitName, int level) {
        playerSelectedTieredKits.put(playerId, kitName + ":" + level);
    }

    public String getSelectedKit(UUID playerId) {
        return playerSelectedTieredKits.get(playerId);
    }

    public boolean equipSelectedKit(Player player, MinigameStatsService statsService) {
        String selectedKit = getSelectedKit(player.getUniqueId());
        if (selectedKit == null) {
            return false; // Pas de kit sélectionné
        }

        String[] parts = selectedKit.split(":");
        if (parts.length != 2)
            return false;

        String kitName = parts[0];
        int level = Integer.parseInt(parts[1]);

        equipTieredKit(player, kitName, level);
        return true;
    }

    private void equipTieredKit(Player player, String kitName, int level) {
        player.getInventory().clear();

        // Clear existing potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Équiper selon le kit et le niveau
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
                // Fallback vers le kit original
                Kit originalKit = originalKitManager.getKit(kitName);
                if (originalKit != null) {
                    originalKit.equipPlayer(player);
                }
                break;
        }
    }

    // Méthodes d'équipement pour chaque kit avec niveaux
    private void equipExplosiveArcher(Player player, int level) {
        ItemStack bow = new ItemStack(Material.BOW);
        if (level >= 2)
            bow.addEnchantment(Enchantment.POWER, 1);
        if (level >= 3)
            bow.addEnchantment(Enchantment.FLAME, 1);
        player.getInventory().setItem(0, bow);

        int arrowCount = 16 + (level * 8); // 24, 32, 40 flèches
        player.getInventory().setItem(1, new ItemStack(Material.ARROW, arrowCount));

        int tntCount = level * 2; // 2, 4, 6 TNT
        player.getInventory().setItem(2, new ItemStack(Material.TNT, tntCount));

        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
        }
    }

    private void equipEnderman(Player player, int level) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        if (level >= 3)
            sword.addEnchantment(Enchantment.SHARPNESS, 1);
        player.getInventory().setItem(0, sword);

        int pearlCount = 2 + level; // 3, 4, 5 perles
        player.getInventory().setItem(1, new ItemStack(Material.ENDER_PEARL, pearlCount));

        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
        }
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0));
        }
    }

    private void equipTank(Player player, int level) {
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        if (level >= 2)
            sword = new ItemStack(Material.IRON_SWORD);
        if (level >= 3)
            sword.addEnchantment(Enchantment.SHARPNESS, 1);
        player.getInventory().setItem(0, sword);

        if (level >= 1) {
            player.getInventory().setItem(8, new ItemStack(Material.SHIELD));
        }

        // Armure selon le niveau
        Material armorMaterial = level == 1 ? Material.LEATHER_CHESTPLATE
                : level == 2 ? Material.CHAINMAIL_CHESTPLATE : Material.IRON_CHESTPLATE;
        player.getInventory().setChestplate(new ItemStack(armorMaterial));

        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
        }
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0));
        }
    }

    private void equipNinja(Player player, int level) {
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        if (level >= 2)
            sword = new ItemStack(Material.IRON_SWORD);
        if (level >= 3)
            sword.addEnchantment(Enchantment.SHARPNESS, 1); // Réduit de 2 à 1
        player.getInventory().setItem(0, sword);

        // Shurikens (boules de neige)
        int shurikenCount = 6 + (level * 2); // 8, 10, 12 shurikens
        player.getInventory().setItem(1, new ItemStack(Material.SNOWBALL, shurikenCount));

        // Effets équilibrés
        int speedLevel = Math.min(level - 1, 1); // Max Speed II
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speedLevel));

        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 0));
        }
        if (level >= 3) {
            // Pas d'invisibilité permanente - trop OP
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1));
        }
    }

    private void equipArcher(Player player, int level) {
        ItemStack bow = new ItemStack(Material.BOW);
        if (level >= 2)
            bow.addEnchantment(Enchantment.POWER, 1);
        if (level >= 3)
            bow.addEnchantment(Enchantment.INFINITY, 1);
        player.getInventory().setItem(0, bow);

        int arrowCount = level >= 3 ? 1 : 32 + (level * 16); // Infinity au niveau 3
        player.getInventory().setItem(1, new ItemStack(Material.ARROW, arrowCount));

        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
        }
    }

    private void equipBerserker(Player player, int level) {
        ItemStack axe = new ItemStack(Material.STONE_AXE);
        if (level >= 2)
            axe = new ItemStack(Material.IRON_AXE);
        if (level >= 3) {
            axe = new ItemStack(Material.IRON_AXE); // Pas de diamond axe
            axe.addEnchantment(Enchantment.SHARPNESS, 1);
        }
        player.getInventory().setItem(0, axe);

        // Strength équilibré
        int strengthLevel = Math.min(level - 1, 1); // Max Strength II
        if (level >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, strengthLevel));
        }

        if (level >= 3) {
            // Seulement un petit bonus au niveau 3
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
        }
    }

    private void equipChemist(Player player, int level) {
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        player.getInventory().setItem(0, sword);

        // Potions selon le niveau
        int potionCount = level * 2; // 2, 4, 6 potions
        player.getInventory().setItem(1, new ItemStack(Material.SPLASH_POTION, potionCount));

        if (level >= 2) {
            player.getInventory().setItem(2, new ItemStack(Material.LINGERING_POTION, level));
        }

        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
        }
    }

    private void equipAssassin(Player player, int level) {
        ItemStack sword = new ItemStack(Material.GOLDEN_SWORD);
        if (level >= 2)
            sword.addEnchantment(Enchantment.SHARPNESS, 1);
        if (level >= 3)
            sword.addEnchantment(Enchantment.SHARPNESS, 2);
        player.getInventory().setItem(0, sword);

        int speedLevel = level - 1; // 0, 1, 2
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speedLevel));

        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 0));
        }
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0));
        }
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

        int blockCount = 16 + (level * 16); // 32, 48, 64 blocs
        player.getInventory().setItem(1, new ItemStack(Material.COBBLESTONE, blockCount));

        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, 0));
        }
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));
        }
    }

    private void equipVampire(Player player, int level) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        if (level >= 3)
            sword.addEnchantment(Enchantment.SHARPNESS, 1);
        player.getInventory().setItem(0, sword);

        if (level >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0));
        }
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
        }
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
        }
    }

    private void equipFrostMage(Player player, int level) {
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        player.getInventory().setItem(0, sword);

        int snowballCount = 16 + (level * 16); // 32, 48, 64 boules de neige
        player.getInventory().setItem(1, new ItemStack(Material.SNOWBALL, snowballCount));

        if (level >= 2) {
            player.getInventory().setItem(2, new ItemStack(Material.ICE, level * 8));
        }

        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
        }
    }

    private void equipJuggernaut(Player player, int level) {
        // Arme équilibrée
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        if (level >= 2) {
            axe = new ItemStack(Material.DIAMOND_AXE);
        }
        if (level >= 3) {
            axe = new ItemStack(Material.DIAMOND_AXE);
            axe.addEnchantment(Enchantment.SHARPNESS, 1); // Réduit de 2 à 1
        }
        player.getInventory().setItem(0, axe);

        // Armure équilibrée (pas de netherite)
        if (level >= 1) {
            player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        }
        if (level >= 2) {
            player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        }
        if (level >= 3) {
            player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        }

        // Effets équilibrés
        if (level >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
        }
        if (level >= 3) {
            // Seulement au niveau 3 et réduit
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
        }
    }

    private void equipTrapper(Player player, int level) {
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        player.getInventory().setItem(0, sword);

        int tripwireCount = 4 + (level * 4); // 8, 12, 16 tripwires
        player.getInventory().setItem(1, new ItemStack(Material.TRIPWIRE_HOOK, tripwireCount));

        int stringCount = 8 + (level * 8); // 16, 24, 32 strings
        player.getInventory().setItem(2, new ItemStack(Material.STRING, stringCount));

        if (level >= 2) {
            player.getInventory().setItem(3, new ItemStack(Material.REDSTONE, level * 8));
        }

        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0));
        }
    }

    private void equipAlchemist(Player player, int level) {
        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        player.getInventory().setItem(0, sword);

        // Potions variées selon le niveau
        int healPotionCount = level * 2; // 2, 4, 6
        player.getInventory().setItem(1, new ItemStack(Material.SPLASH_POTION, healPotionCount));

        if (level >= 2) {
            player.getInventory().setItem(2, new ItemStack(Material.LINGERING_POTION, level));
        }

        if (level >= 3) {
            player.getInventory().setItem(3, new ItemStack(Material.BREWING_STAND));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0));
        }
    }

    private void equipKnockbackWarrior(Player player, int level) {
        // Stick avec knockback selon le niveau (équilibré)
        ItemStack kbStick = new ItemStack(Material.STICK);
        int knockbackLevel = Math.min(2 + level, 4); // 3, 4, 4 knockback (max 4)
        kbStick.addUnsafeEnchantment(Enchantment.KNOCKBACK, knockbackLevel);

        ItemMeta meta = kbStick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + "The Big Stick " + getRomanNumeral(level));
            kbStick.setItemMeta(meta);
        }
        player.getInventory().setItem(0, kbStick);

        // Armure selon le niveau (équilibrée)
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
            // Niveau 3: Amélioration modérée sans être OP
            player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
            player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        }
    }

    private void equipMobility(Player player, int level) {
        // Épée de base
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        player.getInventory().setItem(0, sword);

        // Plumes pour le saut
        int featherCount = 2 + level; // 3, 4, 5 plumes
        player.getInventory().setItem(1, new ItemStack(Material.FEATHER, featherCount));

        // Sucre pour la vitesse
        int sugarCount = 1 + level; // 2, 3, 4 sucres
        player.getInventory().setItem(2, new ItemStack(Material.SUGAR, sugarCount));

        // Armure légère
        player.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));

        // Effets de mobilité selon le niveau (équilibrés)
        int speedLevel = Math.min(level - 1, 1); // Max Speed II
        int jumpLevel = Math.min(level - 1, 1); // Max Jump Boost II

        if (level >= 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speedLevel));
        }
        if (level >= 2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, jumpLevel));
        }
        if (level >= 3) {
            // Niveau 3: Légère amélioration sans être OP
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

    public KitManager getOriginalKitManager() {
        return originalKitManager;
    }
}