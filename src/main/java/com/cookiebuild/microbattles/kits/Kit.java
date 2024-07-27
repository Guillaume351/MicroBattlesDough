package com.cookiebuild.microbattles.kits;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class Kit {
    private final String name;
    private final List<ItemStack> items;
    private ItemStack helmet;
    private ItemStack chestplate;
    private ItemStack leggings;
    private ItemStack boots;

    public Kit(String name) {
        this.name = name;
        this.items = new ArrayList<>();
    }

    public void addItem(Material material, int amount) {
        items.add(new ItemStack(material, amount));
    }

    public void setArmor(Material helmet, Material chestplate, Material leggings, Material boots) {
        this.helmet = new ItemStack(helmet);
        this.chestplate = new ItemStack(chestplate);
        this.leggings = new ItemStack(leggings);
        this.boots = new ItemStack(boots);
    }

    public void equipPlayer(Player player) {
        player.getInventory().clear();
        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
    }

    public String getName() {
        return name;
    }
}