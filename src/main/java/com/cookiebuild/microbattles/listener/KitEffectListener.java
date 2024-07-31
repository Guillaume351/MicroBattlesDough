// Create a new file: KitEffectListener.java

package com.cookiebuild.microbattles.listener;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.kits.Kit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class KitEffectListener implements Listener {

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // get player's game, if it's MicroBattlesGame, get player kit
        Game game = GameManager.getGameOfPlayer(PlayerManager.getPlayer(player));
        if (game != null) {
            if (game instanceof MicroBattlesGame) {
                Kit kit = ((MicroBattlesGame) game).getKit(PlayerManager.getPlayer(player));
                if (kit != null) {
                    switch (kit.getName()) {
                        case "Ninja":
                            if (player.isSneaking()) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
                            }
                            break;
                        case "Frost Mage":
                            if (player.getLocation().getBlock().getTemperature() < 0.15) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
                            }
                            break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            CookiePlayer cookieAttacker = PlayerManager.getPlayer(attacker);
            // get player's game, if it's MicroBattlesGame, get player kit
            Game game = GameManager.getGameOfPlayer(cookieAttacker);
            if (game != null) {
                if (game instanceof MicroBattlesGame) {
                    Kit kit = ((MicroBattlesGame) game).getKit(cookieAttacker);
                    if (kit != null) {
                        switch (kit.getName()) {
                            case "Vampire":
                                attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
                                break;
                            case "Berserker":
                                attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                                break;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // get player's game, if it's MicroBattlesGame, get player kit
        Game game = GameManager.getGameOfPlayer(PlayerManager.getPlayer(player));
        if (game != null) {
            if (game instanceof MicroBattlesGame) {
                Kit kit = ((MicroBattlesGame) game).getKit(PlayerManager.getPlayer(player));
                if (kit != null) {
                    switch (kit.getName()) {
                        case "Alchemist":
                            if (event.getItem() != null && event.getItem().getType() == Material.BREWING_STAND) {
                                // Implement a custom brewing mechanic
                                // give player a MASS item
                                player.getInventory().addItem(new ItemStack(Material.DIAMOND, 1));
                            }
                            break;
                        case "Trapper":
                            if (event.getItem() != null && event.getItem().getType() == Material.TRIPWIRE_HOOK) {
                                // Implement a trap-setting mechanic
                                // give player a MASS item
                                player.getInventory().addItem(new ItemStack(Material.TNT, 1));

                            }
                            break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        // get player's game, if it's MicroBattlesGame, get player kit
        Game game = GameManager.getGameOfPlayer(cookiePlayer);
        if (game != null) {
            if (game instanceof MicroBattlesGame) {
                Kit kit = ((MicroBattlesGame) game).getKit(cookiePlayer);
                if (kit != null && kit.getName().equals("Juggernaut")) {
                    // Implement a death explosion for Juggernaut
                    player.getWorld().createExplosion(player.getLocation(), 2.0f, false, false);
                }
            }
        }

    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow && event.getEntity().getShooter() instanceof Player shooter) {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(shooter);
            Game game = GameManager.getGameOfPlayer(cookiePlayer);

            if (game instanceof MicroBattlesGame && game.hasStarted()) {
                Kit kit = ((MicroBattlesGame) game).getKit(cookiePlayer);
                if (kit != null && kit.getName().equals("Explosive Archer")) {
                    Location loc = event.getEntity().getLocation();
                    loc.getWorld().createExplosion(loc, 1.0F, true, true);
                }
            }
        }
    }
}