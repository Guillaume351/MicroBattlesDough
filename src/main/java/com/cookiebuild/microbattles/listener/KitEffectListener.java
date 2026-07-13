package com.cookiebuild.microbattles.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitManager;

public class KitEffectListener implements Listener {

    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Boolean> assassinInvisibilityBonus = new HashMap<>();
    private final Map<UUID, Long> lastSneakTime = new HashMap<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        if (kit == null)
            return;

        switch (kit.getName()) {
            case "Frost Mage":
                // Effet amélioré : Speed II sur glace/neige + résistance au froid
                Material blockType = player.getLocation().getBlock().getType();
                Material belowType = player.getLocation().subtract(0, 1, 0).getBlock().getType();
                if (blockType == Material.SNOW || blockType == Material.POWDER_SNOW ||
                        belowType == Material.ICE || belowType == Material.PACKED_ICE || belowType == Material.BLUE_ICE
                        ||
                        player.getLocation().getBlock().getTemperature() < 0.15) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0));
                }
                break;
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim))
            return;

        Kit kit = getPlayerKit(attacker);
        if (kit == null)
            return;

        switch (kit.getName()) {
            case "Vampire":
                // Lifesteal amélioré : guérison basée sur les dégâts + chance d'affaiblir
                double damage = event.getDamage();
                double healAmount = Math.min(damage * 0.5, 4.0); // 50% des dégâts, max 2 coeurs
                double newHealth = Math.min(attacker.getHealth() + healAmount, attacker.getMaxHealth());
                attacker.setHealth(newHealth);

                // Effet visuel de guérison
                attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1, 0), 3);

                if (Math.random() < 0.25) { // 25% chance d'affaiblir
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                    victim.sendMessage(
                            ChatColor.DARK_RED + LocaleManager.getMessage("kit.vampire.weakness", victim.locale()));
                }
                break;

            case "Berserker":
                // Effet conditionnel : plus de vie basse = plus de force
                double healthPercentage = attacker.getHealth() / attacker.getMaxHealth();
                if (healthPercentage <= 0.5) { // 50% de vie ou moins
                    int strengthLevel = healthPercentage <= 0.25 ? 1 : 0; // Strength II si 25% ou moins
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, strengthLevel));
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));

                    if (strengthLevel == 1) {
                        attacker.sendMessage(
                                ChatColor.RED + LocaleManager.getMessage("kit.berserker.rage", attacker.locale()));
                        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0f, 0.8f);
                    }
                }
                break;

            case "Assassin":
                // Bonus de dégâts après invisibilité
                if (assassinInvisibilityBonus.getOrDefault(attacker.getUniqueId(), false)) {
                    event.setDamage(event.getDamage() * 2.0); // Double dégâts
                    assassinInvisibilityBonus.put(attacker.getUniqueId(), false);
                    attacker.sendMessage(ChatColor.DARK_PURPLE
                            + LocaleManager.getMessage("kit.assassin.sneak_attack", attacker.locale()));
                    attacker.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 10);
                    attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
                }
                break;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        if (kit == null)
            return;

        ItemStack item = event.getItem();
        if (item == null)
            return;

        switch (kit.getName()) {
            case "Frost Mage":
                if (item.getType() == Material.STICK && event.getAction().isRightClick() &&
                        item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                        item.getItemMeta().getDisplayName().contains("Frost Wand")) {
                    castIceSpell(player);
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
        if (kit == null)
            return;

        if (event.isSneaking() && (kit.getName().equals("Ninja") || kit.getName().equals("Assassin"))) {
            // Système simplifié : double-sneak pour activer l'invisibilité
            long currentTime = System.currentTimeMillis();
            Long lastSneak = lastSneakTime.get(player.getUniqueId());

            if (lastSneak != null && currentTime - lastSneak < 500) { // Double sneak en 0.5s
                activateInvisibility(player, kit);
                lastSneakTime.remove(player.getUniqueId());
            } else {
                lastSneakTime.put(player.getUniqueId(), currentTime);
            }
        }
    }

    private void castIceSpell(Player player) {
        if (!checkCooldown(player, "ice_spell", 8))
            return; // Cooldown réduit

        Location startLoc = player.getLocation();
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        // Pont de glace amélioré
        for (int length = 0; length < 20; length++) { // Plus long
            for (int width = -1; width <= 1; width++) {
                Location bridgeLoc = startLoc.clone().add(direction.clone().multiply(length))
                        .add(perpendicular.clone().multiply(width));
                bridgeLoc.setY(bridgeLoc.getBlockY() - 1);

                Material originalMaterial = bridgeLoc.getBlock().getType();
                if (originalMaterial != Material.AIR && originalMaterial != Material.WATER
                        && originalMaterial != Material.LAVA) {
                    continue; // Ne remplace que l'air, l'eau et la lave
                }

                bridgeLoc.getBlock().setType(Material.PACKED_ICE);

                // Effets visuels
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, bridgeLoc.add(0.5, 1, 0.5), 3);

                // Revert après 8 secondes
                final Location finalLoc = bridgeLoc.clone();
                final Material finalOriginal = originalMaterial;
                Bukkit.getScheduler().runTaskLater(player.getServer().getPluginManager().getPlugin("MicroBattles"),
                        () -> {
                            if (finalLoc.getBlock().getType() == Material.PACKED_ICE) {
                                finalLoc.getBlock().setType(finalOriginal);
                            }
                        }, 160);
            }
        }

        // Zone de gel étendue et plus puissante
        for (Entity entity : player.getNearbyEntities(8, 3, 8)) {
            if (entity instanceof Player target && !entity.equals(player)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 2)); // Slowness III
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 120, 1));
                target.sendMessage(ChatColor.AQUA + LocaleManager.getMessage("kit.frost_mage.frozen", target.locale()));
                target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 15);
            }
        }

        // Effets sonores et visuels améliorés
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 0.5f);
        player.sendMessage(ChatColor.AQUA + LocaleManager.getMessage("kit.frost_mage.ice_bridge", player.locale()));
    }

    private void brewRandomPotion(Player player) {
        if (!checkCooldown(player, "brew_potion", 12))
            return; // Cooldown réduit

        Random random = new Random();
        int potionType = random.nextInt(4); // 4 types différents

        switch (potionType) {
            case 0: // Potion de combat
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 0));
                player.sendMessage(
                        ChatColor.RED + LocaleManager.getMessage("kit.alchemist.potion_combat", player.locale()));
                break;
            case 1: // Potion de mobilité
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 400, 1));
                player.sendMessage(
                        ChatColor.GREEN + LocaleManager.getMessage("kit.alchemist.potion_mobility", player.locale()));
                break;
            case 2: // Potion de guérison
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 1));
                player.sendMessage(
                        ChatColor.LIGHT_PURPLE + "Potion de Guérison brassée ! Régénération et Absorption !");
                break;
            case 3: // Potion tactique
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 600, 0));
                player.sendMessage(
                        ChatColor.GRAY + LocaleManager.getMessage("kit.alchemist.potion_tactical", player.locale()));
                break;
        }

        // Effets visuels et sonores
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 20);
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 1.2f);
    }

    private void activateInvisibility(Player player, Kit kit) {
        String cooldownKey = kit.getName().equals("Assassin") ? "assassin_invisibility" : "ninja_invisibility";
        int cooldown = kit.getName().equals("Assassin") ? 20 : 25;

        if (!checkCooldown(player, cooldownKey, cooldown))
            return;

        // Invisibilité améliorée avec bonus de vitesse
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0)); // Plus long
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 120, 0));

        // Marquer pour le bonus d'assassin
        if (kit.getName().equals("Assassin")) {
            assassinInvisibilityBonus.put(player.getUniqueId(), true);
            player.sendMessage(
                    ChatColor.DARK_PURPLE + LocaleManager.getMessage("kit.assassin.stealth_ready", player.locale()));

            // Retirer le bonus après l'effet
            Bukkit.getScheduler().runTaskLater(player.getServer().getPluginManager().getPlugin("MicroBattles"), () -> {
                assassinInvisibilityBonus.put(player.getUniqueId(), false);
            }, 120);
        }

        // Effets visuels et sonores
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GRAY + LocaleManager.getMessage("kit.ninja.stealth", player.locale()));
    }

    private boolean checkCooldown(Player player, String ability, int cooldownSeconds) {
        String key = player.getUniqueId() + "_" + ability;
        long currentTime = System.currentTimeMillis();
        long lastUsed = cooldowns.getOrDefault(key, 0L);

        if (currentTime - lastUsed < cooldownSeconds * 1000L) {
            long remainingSeconds = (cooldownSeconds * 1000L - (currentTime - lastUsed)) / 1000;
            player.sendMessage(
                    ChatColor.RED + "Cette capacité est en cooldown pour " + remainingSeconds + " secondes de plus.");
            return false;
        }

        cooldowns.put(key, currentTime);
        return true;
    }

    private Kit getPlayerKit(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = GameManager.getGameOfPlayer(cookiePlayer);
        if (!(game instanceof MicroBattlesGame) || !game.hasStarted()) {
            return null;
        }

        KitManager kitManager = KitManager.getInstance();
        String selectedKitStr = kitManager.getSelectedKit(player.getUniqueId());

        if (selectedKitStr == null || selectedKitStr.isEmpty()) {
            return kitManager.getOriginalKit("Default");
        }

        String[] parts = selectedKitStr.split(":");
        String kitName = parts[0];

        return kitManager.getOriginalKit(kitName);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity().getShooter() instanceof Player shooter) {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(shooter);
            Game game = GameManager.getGameOfPlayer(cookiePlayer);

            if (game instanceof MicroBattlesGame microBattlesGame && game.hasStarted()) {
                Kit playerKit = getPlayerKit(shooter);
                if (playerKit == null)
                    return;

                // Explosive Archer - Flèches explosives
                if (event.getEntity() instanceof Arrow && playerKit.getName().equals("Explosive Archer")) {
                    Location explosionLoc = event.getEntity().getLocation();
                    event.getEntity().getWorld().createExplosion(explosionLoc, 2.5F, false, false);

                    // Dégâts aux joueurs proches
                    for (Entity entity : event.getEntity().getNearbyEntities(4, 4, 4)) {
                        if (entity instanceof Player target && !entity.equals(shooter)) {
                            target.damage(6.0, shooter); // Dégâts directs
                            target.setVelocity(
                                    target.getLocation().subtract(explosionLoc).toVector().normalize().multiply(1.2));
                        }
                    }
                    event.getEntity().remove();
                }

                // Frost Mage - Boules de neige ralentissantes
                else if (event.getEntity() instanceof Snowball && playerKit.getName().equals("Frost Mage")) {
                    Location hitLoc = event.getEntity().getLocation();

                    // Zone de gel
                    for (Entity entity : event.getEntity().getNearbyEntities(3, 3, 3)) {
                        if (entity instanceof Player target && !entity.equals(shooter)) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
                            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1));
                            target.damage(2.0, shooter);
                            target.sendMessage(ChatColor.AQUA
                                    + LocaleManager.getMessage("kit.frost_mage.frozen_snowball", target.locale()));
                        }
                    }

                    // Effets visuels
                    hitLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, hitLoc, 30);
                    hitLoc.getWorld().playSound(hitLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
                }
            }
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player shooter) {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(shooter);
            Game game = GameManager.getGameOfPlayer(cookiePlayer);

            if (game instanceof MicroBattlesGame microBattlesGame && game.hasStarted()) {
                Kit playerKit = getPlayerKit(shooter);
                if (playerKit == null)
                    return;

                // Frost Mage - Lancement automatique de boules de neige avec le wand
                if (event.getEntity() instanceof Snowball && playerKit.getName().equals("Frost Mage")) {
                    ItemStack item = shooter.getInventory().getItemInMainHand();
                    if (item.getType() == Material.STICK && item.hasItemMeta() &&
                            item.getItemMeta().hasDisplayName() &&
                            item.getItemMeta().getDisplayName().contains("Frost Wand")) {

                        // Améliorer la boule de neige
                        event.getEntity().setVelocity(event.getEntity().getVelocity().multiply(1.5));
                        shooter.getWorld().spawnParticle(Particle.SNOWFLAKE, shooter.getLocation().add(0, 1, 0), 10);
                    }
                }
            }
        }
    }
}
