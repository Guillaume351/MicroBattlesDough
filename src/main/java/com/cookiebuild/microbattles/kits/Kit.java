package com.cookiebuild.microbattles.kits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.cookiebuild.cookiedough.utils.LocaleManager;

public class Kit {
    private final String name;
    private final List<ItemStack> items;
    private ItemStack helmet;
    private ItemStack chestplate;
    private ItemStack leggings;
    private ItemStack boots;
    private final int price;
    private final int requiredLevel;
    private final boolean defaultUnlocked;
    private final String description;

    public Kit(String name, int price, int requiredLevel, boolean defaultUnlocked, String description) {
        this.name = name;
        this.items = new ArrayList<>();
        this.price = price;
        this.requiredLevel = requiredLevel;
        this.defaultUnlocked = defaultUnlocked;
        this.description = description;
    }

    public void addItem(Material material, int amount) {
        items.add(new ItemStack(material, amount));
    }

    public void addItem(Material material, int amount, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material, amount);
        item.addEnchantment(enchantment, level);
        items.add(item);
    }

    public void addItemStack(ItemStack item, int amount) {
        for (int i = 0; i < amount; i++) {
            items.add(item);
        }
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

    public int getPrice() {
        return price;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public boolean isDefaultUnlocked() {
        return defaultUnlocked;
    }

    public String getDescription() {
        return description;
    }

    public String getLocalizedDescription(Locale locale) {
        String key = "kit." + name.toLowerCase().replace(" ", "_") + ".description";
        return LocaleManager.getMessage(key, locale);
    }
}