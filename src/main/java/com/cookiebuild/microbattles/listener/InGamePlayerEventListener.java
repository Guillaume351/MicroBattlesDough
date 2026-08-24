package com.cookiebuild.microbattles.listener;

import java.util.ArrayList;

import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.attribute.Attribute;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.BaseEventBlocker;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.map.MapManager;

public class InGamePlayerEventListener extends BaseEventBlocker {

    public InGamePlayerEventListener() {
        protectedWorlds = new ArrayList<>();
    }

    public void addProtectedWorld(String worldName) {
        protectedWorlds.add(worldName);
    }

    public void removeProtectedWorld(String worldName) {
        protectedWorlds.remove(worldName);
    }

    private boolean isPlayerInGame(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null) {
            return false;
        }
        Game game = GameManager.getGameOfPlayer(cookiePlayer);
        return cookiePlayer.getState() == PlayerState.IN_GAME
                && game instanceof MicroBattlesGame;
    }

    private boolean isGameRunning(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = GameManager.getGameOfPlayer(cookiePlayer);
        return game != null && game.hasStarted();
    }

    @Override
    protected boolean shouldAllowBlockBreak(BlockBreakEvent event) {
        return isPlayerInGame(event.getPlayer()) && isGameRunning(event.getPlayer())
                && event.getBlock().getType() != org.bukkit.Material.GLASS_PANE;
    }

    @Override
    protected boolean shouldAllowBlockPlace(BlockPlaceEvent event) {
        return isPlayerInGame(event.getPlayer()) && isGameRunning(event.getPlayer());
    }

    @Override
    protected boolean shouldAllowPlayerInteract(PlayerInteractEvent event) {
        return isPlayerInGame(event.getPlayer());
    }

    @Override
    protected boolean shouldAllowEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return true; // Allow damage to non-player entities
        }

        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = GameManager.getGameOfPlayer(cookiePlayer);

        if (!(game instanceof MicroBattlesGame microBattlesGame) || !isGameRunning(player)) {
            player.setFireTicks(0);
            return false; // Prevent damage if not in a running MicroBattlesGame
        }

        if (event instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            Player damager = null;
            if (damageByEntityEvent.getDamager() instanceof Player directDamager) {
                damager = directDamager;
            } else if (damageByEntityEvent.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player shooter) {
                damager = shooter;
            }
            if (damager != null) {
                CookiePlayer damagerCookiePlayer = PlayerManager.getPlayer(damager);
                Game damagerGame = GameManager.getGameOfPlayer(damagerCookiePlayer);
                if (damagerCookiePlayer == null || damagerGame != microBattlesGame
                        || damager.getGameMode() == GameMode.SPECTATOR
                        || microBattlesGame.arePlayersInSameTeam(cookiePlayer, damagerCookiePlayer)) {
                    return false;
                }
                cookiePlayer.getPlayer().setKiller(damager);
                microBattlesGame.recordDamage(cookiePlayer, damagerCookiePlayer);
                return true;
            }
        }

        return true;
    }

    @Override
    protected boolean shouldAllowProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return true; // Allow projectiles from non-player sources
        }
        return isPlayerInGame(player) && isGameRunning(player);
    }

    @Override
    protected boolean shouldAllowPlayerDropItem(PlayerDropItemEvent event) {
        return false;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!protectedWorlds.contains(event.getPlayer().getWorld().getName())) {
            return;
        }
        Player player = event.getPlayer();
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = GameManager.getGameOfPlayer(cookiePlayer);

        if (game instanceof MicroBattlesGame microBattlesGame) {
            if (player.getLocation().getY() < 0) { // Adjust this value based on your map
                if (isGameRunning(player)) {
                    microBattlesGame.handlePlayerFall(cookiePlayer);
                } else {
                    // Respawn player to team spawn even if game hasn't started
                    microBattlesGame.respawnPlayerToTeamSpawn(cookiePlayer);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = GameManager.getGameOfPlayer(cookiePlayer);

        if (game instanceof MicroBattlesGame microBattlesGame && isGameRunning(player)) {
            event.setCancelled(true); // Prevent default death behavior
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.deathMessage(null);
            var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());

            // Check if the player was killed by another player
            Player killer = player.getKiller();
            if (killer != null) {
                // Send a message to the killer
                killer.sendMessage(
                        "§a" + LocaleManager.getMessage("game.player_eliminated", killer.locale(), player.getName()));
                // play a sound to the killer
                killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1, 1);
            }

            microBattlesGame.handlePlayerDeath(cookiePlayer, PlayerManager.getPlayer(killer));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var playerId = event.getPlayer().getUniqueId();
        MapManager.removeNextMapVote(playerId);
        KitManager.getInstance().clearSelectedKit(playerId);
        removeFromMicroBattles(event.getPlayer(), "disconnect");
    }

    /** Core lobby handling only removes IN_GAME players, so spectators leave here first. */
    @EventHandler
    public void onLobbyCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT);
        if (command.equals("/lobby") || command.startsWith("/lobby ")) {
            removeFromMicroBattles(event.getPlayer(), "returned_lobby");
        }
    }

    private void removeFromMicroBattles(Player player) {
        removeFromMicroBattles(player, "left_game");
    }

    private void removeFromMicroBattles(Player player, String reason) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = GameManager.getGameOfPlayer(cookiePlayer);
        if (game instanceof MicroBattlesGame microBattlesGame) {
            microBattlesGame.removePlayer(cookiePlayer, reason);
        }
    }

    @EventHandler
    public void onSpectatorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isPlayerInGame(player) && player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFireSpread(BlockIgniteEvent event) {
        if (!protectedWorlds.contains(event.getBlock().getWorld().getName())) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !isPlayerInGame(player) || !isGameRunning(player)) {
            event.setCancelled(true);
        }
    }

}
