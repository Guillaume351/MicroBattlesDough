package com.cookiebuild.microbattles.kits;

import com.cookiebuild.cookiedough.utils.PotionUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
        createPyromancerKit();

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
    }

    public static KitManager getInstance() {
        return instance;
    }

    private void createDefaultKit() {
        Kit defaultKit = new Kit("Default");
        defaultKit.addItem(Material.STONE_SWORD, 1);
        defaultKit.addItem(Material.BOW, 1);
        defaultKit.addItem(Material.GOLDEN_PICKAXE, 1);
        defaultKit.addItem(Material.ARROW, 32);
        defaultKit.addItem(Material.CARROT, 3);
        defaultKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(defaultKit);
    }

    private void createExplosiveArcherKit() {
        Kit explosiveArcherKit = new Kit("Explosive Archer");
        explosiveArcherKit.addItem(Material.BOW, 1, Enchantment.POWER, 2);
        explosiveArcherKit.addItem(Material.ARROW, 64);
        explosiveArcherKit.addItem(Material.GOLDEN_PICKAXE, 1);
        explosiveArcherKit.addItem(Material.TNT, 5);
        explosiveArcherKit.setArmor(Material.LEATHER_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(explosiveArcherKit);
    }

    private void createEndermanKit() {
        Kit endermanKit = new Kit("Enderman");
        endermanKit.addItem(Material.ENDER_PEARL, 16);
        endermanKit.addItem(Material.STONE_SWORD, 1);
        endermanKit.addItem(Material.GOLDEN_PICKAXE, 1);
        endermanKit.addItem(Material.CHORUS_FRUIT, 5);
        endermanKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(endermanKit);
    }

    private void createKnockbackWarriorKit() {
        Kit knockbackWarriorKit = new Kit("Knockback Warrior");

        knockbackWarriorKit.addItem(Material.WOODEN_SWORD, 1, Enchantment.KNOCKBACK, 2);
        knockbackWarriorKit.addItem(Material.FISHING_ROD, 1);
        knockbackWarriorKit.setArmor(Material.CHAINMAIL_HELMET, Material.IRON_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS);
        kits.add(knockbackWarriorKit);
    }

    private void createTankKit() {
        Kit tankKit = new Kit("Tank");
        tankKit.addItem(Material.IRON_SWORD, 1);
        tankKit.addItem(Material.SHIELD, 1);
        tankKit.addItem(Material.GOLDEN_APPLE, 2);
        tankKit.setArmor(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
        kits.add(tankKit);
    }

    private void createNinjaKit() {
        Kit ninjaKit = new Kit("Ninja");
        ninjaKit.addItem(Material.IRON_SWORD, 1, Enchantment.SHARPNESS, 2);
        ninjaKit.addItem(Material.ENDER_PEARL, 3);
        ninjaKit.addItemStack(PotionUtil.createPotion(PotionEffectType.SPEED, 3600, 0), 1); // 3 minutes of Speed I
        ninjaKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(ninjaKit);
    }

    private void createPyromancerKit() {
        Kit pyromancerKit = new Kit("Pyromancer");
        pyromancerKit.addItem(Material.FIRE_CHARGE, 8);
        pyromancerKit.addItem(Material.FLINT_AND_STEEL, 1);
        pyromancerKit.addItem(Material.BLAZE_ROD, 1);
        pyromancerKit.setArmor(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS);
        kits.add(pyromancerKit);
    }

    private void createArcherKit() {
        Kit archerKit = new Kit("Archer");
        archerKit.addItem(Material.BOW, 1, Enchantment.POWER, 3);
        archerKit.addItem(Material.ARROW, 64);
        archerKit.addItem(Material.STONE_SWORD, 1);
        archerKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(archerKit);
    }

    private void createBerserkerKit() {
        Kit berserkerKit = new Kit("Berserker");
        berserkerKit.addItem(Material.IRON_AXE, 1, Enchantment.SHARPNESS, 2);
        berserkerKit.addItem(Material.GOLDEN_APPLE, 2);
        berserkerKit.setArmor(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS);
        kits.add(berserkerKit);
    }

    private void createChemistKit() {
        Kit chemistKit = new Kit("Chemist");
        chemistKit.addItemStack(PotionUtil.createSplashPotion(PotionEffectType.INSTANT_DAMAGE, 1, 0), 3); // Instant Damage I
        chemistKit.addItemStack(PotionUtil.createLingeringPotion(PotionEffectType.POISON, 200, 0), 2); // Poison I for 10 seconds
        chemistKit.addItem(Material.WOODEN_SWORD, 1);
        chemistKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(chemistKit);
    }

    private void createAssassinKit() {
        Kit assassinKit = new Kit("Assassin");
        assassinKit.addItem(Material.IRON_SWORD, 1, Enchantment.SHARPNESS, 1);
        assassinKit.addItem(Material.ENDER_PEARL, 2);
        assassinKit.addItemStack(PotionUtil.createPotion(PotionEffectType.INVISIBILITY, 1200, 0), 1); // Invisibility for 1 minute
        assassinKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(assassinKit);
    }

    private void createMinerKit() {
        Kit minerKit = new Kit("Miner");
        minerKit.addItem(Material.IRON_PICKAXE, 1, Enchantment.EFFICIENCY, 3);
        minerKit.addItem(Material.STONE, 64);
        minerKit.addItem(Material.TNT, 3);
        minerKit.addItem(Material.REDSTONE_TORCH, 1);
        minerKit.setArmor(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS);
        kits.add(minerKit);
    }

    private void createVampireKit() {
        Kit vampireKit = new Kit("Vampire");
        vampireKit.addItem(Material.WOODEN_SWORD, 1);
        vampireKit.addItem(Material.REDSTONE, 8); // Represents blood
        vampireKit.addItemStack(PotionUtil.createPotion(PotionEffectType.REGENERATION, 600, 0), 1);// Regeneration I for 30 seconds
        vampireKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(vampireKit);
    }

    private void createFrostMageKit() {
        Kit frostMageKit = new Kit("Frost Mage");

        frostMageKit.addItem(Material.DIAMOND_HOE, 1); // Ice wand
        frostMageKit.addItem(Material.SNOWBALL, 8);
        frostMageKit.addItem(Material.PACKED_ICE, 32);
        frostMageKit.setArmor(Material.DIAMOND_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(frostMageKit);
    }

    private void createJuggernautKit() {
        Kit juggernautKit = new Kit("Juggernaut");
        juggernautKit.addItem(Material.STONE_AXE, 1);
        juggernautKit.addItem(Material.SHIELD, 1);
        juggernautKit.setArmor(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);
        kits.add(juggernautKit);
    }

    private void createTrapperKit() {
        Kit trapperKit = new Kit("Trapper");
        trapperKit.addItem(Material.STONE_SWORD, 1);
        trapperKit.addItem(Material.TRIPWIRE_HOOK, 16);
        trapperKit.addItem(Material.REDSTONE, 32);
        trapperKit.addItem(Material.PISTON, 8);
        trapperKit.addItem(Material.TNT, 2);
        trapperKit.addItem(Material.REDSTONE_TORCH, 4);
        trapperKit.addItem(Material.STRING, 64);
        trapperKit.addItem(Material.REPEATER, 4);
        trapperKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(trapperKit);
    }

    private void createAlchemistKit() {
        Kit alchemistKit = new Kit("Alchemist");
        alchemistKit.addItem(Material.STONE_SWORD, 1);
        alchemistKit.addItem(Material.BREWING_STAND, 4);
        alchemistKit.setArmor(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS);
        kits.add(alchemistKit);
    }

    public Kit getKit(String name) {
        for (Kit kit : kits) {
            if (kit.getName().equals(name)) {
                return kit;
            }
        }
        return null;
    }

    public Kit getRandomKit() {
        Random random = new Random();
        return kits.get(random.nextInt(kits.size()));
    }
}