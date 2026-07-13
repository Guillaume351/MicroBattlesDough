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
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitManager;

/** Runtime kit abilities. Every offensive effect is scoped to enemies in the same match. */
public class KitEffectListener implements Listener {
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Boolean> assassinInvisibilityBonus = new HashMap<>();
    private final Map<UUID, Long> lastSneakTime = new HashMap<>();
    private final Random random = new Random();

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        if (kit == null || !kit.getName().equals("Frost Mage")) {
            return;
        }
        Material atFeet = player.getLocation().getBlock().getType();
        Material below = player.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
        if (atFeet == Material.SNOW || atFeet == Material.POWDER_SNOW || below == Material.ICE
                || below == Material.PACKED_ICE || below == Material.BLUE_ICE) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, true, false));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)
                || !isEnemy(attacker, victim)) {
            return;
        }
        Kit kit = getPlayerKit(attacker);
        if (kit == null) {
            return;
        }
        int tier = getSelectedTier(attacker);
        switch (kit.getName()) {
            case "Vampire" -> {
                double ratio = switch (tier) {
                    case 3 -> 0.30;
                    case 2 -> 0.25;
                    default -> 0.20;
                };
                double cap = 0.75 + (tier * 0.25);
                double maxHealth = getMaxHealth(attacker);
                attacker.setHealth(Math.min(maxHealth,
                        attacker.getHealth() + Math.min(event.getDamage() * ratio, cap)));
                attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1, 0), 2);
                double weaknessChance = tier == 3 ? 0.15 : tier == 2 ? 0.12 : 0.10;
                if (random.nextDouble() < weaknessChance) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 0));
                }
            }
            case "Berserker" -> {
                if (attacker.getHealth() / getMaxHealth(attacker) <= 0.30) {
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 50 + (tier * 10), 0));
                    attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 0.6f, 0.9f);
                }
            }
            case "Assassin" -> {
                if (assassinInvisibilityBonus.remove(attacker.getUniqueId()) != null) {
                    double multiplier = 1.30 + (Math.max(1, tier) * 0.05);
                    event.setDamage(event.getDamage() * multiplier);
                    attacker.sendMessage(ChatColor.DARK_PURPLE
                            + LocaleManager.getMessage("kit.assassin.sneak_attack", attacker.locale()));
                    attacker.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 8);
                }
            }
            default -> {
                // This kit has no melee proc.
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        ItemStack item = event.getItem();
        if (kit == null || item == null || !event.getAction().isRightClick()) {
            return;
        }
        if (kit.getName().equals("Frost Mage") && isNamedItem(item, Material.STICK, "Frost Wand")) {
            event.setCancelled(true);
            castIceSpell(player, getSelectedTier(player));
        } else if (kit.getName().equals("Alchemist") && item.getType() == Material.BREWING_STAND) {
            event.setCancelled(true);
            brewRandomPotion(player, getSelectedTier(player));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        Kit kit = getPlayerKit(player);
        if (kit == null || !event.isSneaking()
                || (!kit.getName().equals("Ninja") && !kit.getName().equals("Assassin"))) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastSneakTime.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 500) {
            lastSneakTime.remove(player.getUniqueId());
            activateInvisibility(player, kit, getSelectedTier(player));
        }
    }

    private void castIceSpell(Player player, int tier) {
        int safeTier = Math.max(1, tier);
        int cooldown = 18 - (safeTier * 2);
        if (!checkCooldown(player, "ice_spell", cooldown)) {
            return;
        }
        int length = 6 + (safeTier * 2);
        Location start = player.getLocation();
        Vector direction = start.getDirection().setY(0);
        if (direction.lengthSquared() == 0) {
            return;
        }
        direction.normalize();
        for (int offset = 0; offset < length; offset++) {
            Location bridgeLocation = start.clone().add(direction.clone().multiply(offset));
            bridgeLocation.setY(bridgeLocation.getBlockY() - 1);
            Material original = bridgeLocation.getBlock().getType();
            if (original != Material.AIR && original != Material.WATER && original != Material.LAVA) {
                continue;
            }
            bridgeLocation.getBlock().setType(Material.PACKED_ICE);
            Location restoreLocation = bridgeLocation.clone();
            Bukkit.getScheduler().runTaskLater(MicroBattles.getInstance(), () -> {
                if (restoreLocation.getBlock().getType() == Material.PACKED_ICE) {
                    restoreLocation.getBlock().setType(original);
                }
            }, 120L);
        }
        for (Entity entity : player.getNearbyEntities(4, 2, 4)) {
            if (entity instanceof Player target && isEnemy(player, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 45 + safeTier * 10,
                        safeTier == 3 ? 1 : 0));
            }
        }
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 16);
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.2f);
    }

    private void brewRandomPotion(Player player, int tier) {
        int safeTier = Math.max(1, tier);
        if (!checkCooldown(player, "brew_potion", 16 - safeTier)) {
            return;
        }
        int duration = 100 + safeTier * 20;
        switch (random.nextInt(4)) {
            case 0 -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 0));
            case 1 -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration + 60, 0));
            case 2 -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0));
            default -> player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration + 80, 0));
        }
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 12);
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.1f);
    }

    private void activateInvisibility(Player player, Kit kit, int tier) {
        boolean assassin = kit.getName().equals("Assassin");
        int safeTier = Math.max(1, tier);
        if (!checkCooldown(player, assassin ? "assassin_stealth" : "ninja_stealth",
                (assassin ? 23 : 27) - safeTier)) {
            return;
        }
        int duration = 45 + safeTier * 10;
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0));
        if (assassin) {
            assassinInvisibilityBonus.put(player.getUniqueId(), true);
            Bukkit.getScheduler().runTaskLater(MicroBattles.getInstance(),
                    () -> assassinInvisibilityBonus.remove(player.getUniqueId()), duration);
        }
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 12);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.0f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        MicroBattlesGame game = getRunningGame(shooter);
        Kit kit = getPlayerKit(shooter);
        if (game == null || kit == null) {
            return;
        }
        int tier = Math.max(1, getSelectedTier(shooter));
        Location hit = event.getEntity().getLocation();
        if (event.getEntity() instanceof Arrow && kit.getName().equals("Explosive Archer")) {
            shooter.getWorld().spawnParticle(Particle.EXPLOSION, hit, 2);
            shooter.getWorld().playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
            for (Entity entity : event.getEntity().getNearbyEntities(3, 3, 3)) {
                if (entity instanceof Player target && isEnemy(shooter, target)) {
                    target.damage(1.5 + (tier * 0.5), shooter);
                    Vector knockback = target.getLocation().toVector().subtract(hit.toVector());
                    if (knockback.lengthSquared() > 0) {
                        target.setVelocity(knockback.normalize().multiply(0.45 + tier * 0.10).setY(0.25));
                    }
                }
            }
            event.getEntity().remove();
        } else if (event.getEntity() instanceof Snowball && kit.getName().equals("Frost Mage")) {
            for (Entity entity : event.getEntity().getNearbyEntities(2.5, 2.5, 2.5)) {
                if (entity instanceof Player target && isEnemy(shooter, target)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 45 + tier * 10,
                            tier == 3 ? 1 : 0));
                    target.damage(0.5 + tier * 0.5, shooter);
                }
            }
            hit.getWorld().spawnParticle(Particle.SNOWFLAKE, hit, 16);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (getRunningGame(event.getPlayer()) == null) {
            clearPlayerState(event.getPlayer().getUniqueId());
        }
    }

    private void clearPlayerState(UUID playerId) {
        cooldowns.remove(playerId);
        assassinInvisibilityBonus.remove(playerId);
        lastSneakTime.remove(playerId);
    }

    private boolean checkCooldown(Player player, String ability, int cooldownSeconds) {
        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long lastUsed = playerCooldowns.getOrDefault(ability, 0L);
        long cooldownMillis = cooldownSeconds * 1_000L;
        if (now - lastUsed < cooldownMillis) {
            long remaining = Math.max(1, (cooldownMillis - (now - lastUsed) + 999L) / 1_000L);
            player.sendMessage(ChatColor.RED + "Ability ready in " + remaining + "s.");
            return false;
        }
        playerCooldowns.put(ability, now);
        return true;
    }

    private Kit getPlayerKit(Player player) {
        if (getRunningGame(player) == null) {
            return null;
        }
        String selection = KitManager.getInstance().getSelectedKit(player.getUniqueId());
        String kitName = selection == null || selection.isBlank() ? "Default" : selection.split(":", 2)[0];
        return KitManager.getInstance().getOriginalKit(kitName);
    }

    private int getSelectedTier(Player player) {
        return KitManager.getInstance().getSelectedTier(player.getUniqueId());
    }

    private MicroBattlesGame getRunningGame(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = cookiePlayer == null ? null : GameManager.getGameOfPlayer(cookiePlayer);
        return game instanceof MicroBattlesGame microBattlesGame && game.hasStarted() ? microBattlesGame : null;
    }

    private boolean isEnemy(Player source, Player target) {
        CookiePlayer sourcePlayer = PlayerManager.getPlayer(source);
        CookiePlayer targetPlayer = PlayerManager.getPlayer(target);
        Game sourceGame = sourcePlayer == null ? null : GameManager.getGameOfPlayer(sourcePlayer);
        Game targetGame = targetPlayer == null ? null : GameManager.getGameOfPlayer(targetPlayer);
        return sourceGame instanceof MicroBattlesGame microBattlesGame && sourceGame == targetGame
                && sourceGame.hasStarted() && !microBattlesGame.arePlayersInSameTeam(sourcePlayer, targetPlayer);
    }

    private boolean isNamedItem(ItemStack item, Material material, String nameFragment) {
        return item.getType() == material && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains(nameFragment);
    }

    private double getMaxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }
}
