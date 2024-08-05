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
import org.bukkit.util.Vector;

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

        Location startLoc = player.getLocation();
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        for (int length = 0; length < 8; length++) {
            for (int width = -1; width <= 1; width++) {
                Location bridgeLoc = startLoc.clone().add(direction.clone().multiply(length)).add(perpendicular.clone().multiply(width));

                // Ensure the bridge is placed at foot level
                bridgeLoc.setY(bridgeLoc.getBlockY() - 1);

                Material originalMaterial = bridgeLoc.getBlock().getType();
                bridgeLoc.getBlock().setType(Material.PACKED_ICE);

                // Schedule the ice to revert to the original block after 5 seconds (100 ticks)
                Bukkit.getScheduler().runTaskLater(player.getServer().getPluginManager().getPlugin("MicroBattles"), () -> {
                    if (bridgeLoc.getBlock().getType() == Material.PACKED_ICE) {
                        bridgeLoc.getBlock().setType(originalMaterial);
                    }
                }, 100);
            }
        }

        // Apply slowness effect to nearby players
        for (Entity entity : player.getNearbyEntities(6, 2, 6)) {
            if (entity instanceof Player && !entity.equals(player)) {
                ((Player) entity).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            }
        }

        // Play a sound effect
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
    }

    private void createFireRing(Player player) {
        if (!checkCooldown(player, "fire_ring", 15)) return;

        Location center = player.getLocation();
        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            Location loc = center.clone().add(2 * Math.cos(angle), 0, 2 * Math.sin(angle));
            loc.getBlock().setType(Material.FIRE);
        }

        // Give fire immunity to the player
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 120, 0));

        // Schedule task to remove fire and fire resistance
        Bukkit.getScheduler().runTaskLater(player.getServer().getPluginManager().getPlugin("MicroBattles"), () -> {
            // Remove fire
            for (int i = 0; i < 16; i++) {
                double angle = 2 * Math.PI * i / 16;
                Location loc = center.clone().add(2 * Math.cos(angle), 0, 2 * Math.sin(angle));
                if (loc.getBlock().getType() == Material.FIRE) {
                    loc.getBlock().setType(Material.AIR);
                }
            }

            // Remove fire resistance from the player
            player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            player.setFireTicks(0);
        }, 20 * 8);
    }

    private void brewRandomPotion(Player player) {
        if (!checkCooldown(player, "brew_potion", 15)) return;

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