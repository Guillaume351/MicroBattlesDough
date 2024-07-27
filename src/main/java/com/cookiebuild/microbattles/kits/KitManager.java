package com.cookiebuild.microbattles.kits;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class KitManager {
    private static final KitManager instance = new KitManager();
    List<Kit> kits = new ArrayList<>();

    private KitManager() {
        // register kits
        createDefaultKit();
    }

    public static KitManager getInstance() {
        return instance;
    }

    private void createDefaultKit() {
        Kit defaultKit = new Kit("Default");
        defaultKit.addItem(Material.STONE_SWORD, 1);
        defaultKit.addItem(Material.BOW, 1);
        defaultKit.addItem(Material.ARROW, 32);
        defaultKit.setArmor(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        kits.add(defaultKit);
    }

    public Kit getKit(String name) {
        for (Kit kit : kits) {
            if (kit.getName().equals(name)) {
                return kit;
            }
        }
        return null;
    }
}
