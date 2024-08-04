package com.cookiebuild.microbattles.listener;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.kits.Kit;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class KitEffectListener implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Boolean> ninjaInvisibility = new HashMap<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        if (kit == null) return;

        switch (kit.getName()) {
            case "Frost Mage":
                if (player.getLocation().getBlock().getTemperature() < 0.15) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
                }
                break;
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;

        Kit kit = getPlayerKit(attacker);
        if (kit == null) return;

        switch (kit.getName()) {
            case "Vampire":
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
                if (Math.random() < 0.2) { // 20% chance
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                }
                break;
            case "Berserker":
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0));
                break;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        if (kit == null) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        switch (kit.getName()) {
            case "Frost Mage":
                if (item.getType() == Material.DIAMOND_HOE && event.getAction().isRightClick()) {
                    castIceSpell(player);
                }
                break;
            case "Pyromancer":
                if (item.getType() == Material.BLAZE_ROD && event.getAction().isRightClick()) {
                    createFireRing(player);
                }
                break;
            case "Alchemist":
                if (item.getType() == Material.BREWING_STAND && event.getAction().isRightClick()) {
                    brewRandomPotion(player);
                }
                break;
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        if (kit == null || !kit.getName().equals("Ninja")) return;

        if (event.isSneaking()) {
            if (ninjaInvisibility.getOrDefault(player.getUniqueId(), false)) {
                activateNinjaInvisibility(player);
            } else {
                ninjaInvisibility.put(player.getUniqueId(), true);
                Bukkit.getScheduler().runTaskLater(player.getServer().getPluginManager().getPlugin("MicroBattles"), () -> {
                    ninjaInvisibility.put(player.getUniqueId(), false);
                }, 10);
            }
        }
    }

    private void castIceSpell(Player player) {
        if (!checkCooldown(player, "ice_spell", 10)) return;

        Location center = player.getLocation();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location loc = center.clone().add(x, -1, z);
                loc.getBlock().setType(Material.PACKED_ICE);
                Bukkit.getScheduler().runTaskLater(player.getServer().getPluginManager().getPlugin("MicroBattles"), () -> {
                    loc.getBlock().setType(Material.AIR);
                }, 100);
            }
        }

        for (Entity entity : player.getNearbyEntities(3, 2, 3)) {
            if (entity instanceof Player) {
                ((Player) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            }
        }
    }

    private void createFireRing(Player player) {
        if (!checkCooldown(player, "fire_ring", 15)) return;

        Location center = player.getLocation();
        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            Location loc = center.clone().add(2 * Math.cos(angle), 0, 2 * Math.sin(angle));
            loc.getBlock().setType(Material.FIRE);
            Bukkit.getScheduler().runTaskLater(player.getServer().getPluginManager().getPlugin("MicroBattles"), () -> {
                if (loc.getBlock().getType() == Material.FIRE) {
                    loc.getBlock().setType(Material.AIR);
                }
            }, 100);
        }
    }

    private void brewRandomPotion(Player player) {
        if (!checkCooldown(player, "brew_potion", 30)) return;

        PotionEffectType[] potionTypes = {PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.REGENERATION};
        PotionEffectType randomEffect = potionTypes[new Random().nextInt(potionTypes.length)];
        player.addPotionEffect(new PotionEffect(randomEffect, 200, 0));
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.0f);
    }

    private void activateNinjaInvisibility(Player player) {
        if (!checkCooldown(player, "ninja_invisibility", 30)) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);
    }

    private boolean checkCooldown(Player player, String ability, int cooldownSeconds) {
        long currentTime = System.currentTimeMillis();
        long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (currentTime - lastUsed < cooldownSeconds * 1000L) {
            player.sendMessage(ChatColor.RED + "This ability is on cooldown for " +
                    ((cooldownSeconds * 1000L - (currentTime - lastUsed)) / 1000) + " more seconds.");
            return false;
        }

        cooldowns.put(player.getUniqueId(), currentTime);
        return true;
    }

    private Kit getPlayerKit(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = GameManager.getGameOfPlayer(cookiePlayer);
        if (!(game instanceof MicroBattlesGame microBattlesGame) || !game.hasStarted()) {
            return null;
        }
        return microBattlesGame.getKit(cookiePlayer);
    }
}