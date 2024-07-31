package com.cookiebuild.microbattles.listener;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.BaseEventBlocker;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;

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
        Game game = GameManager.getGameOfPlayer(cookiePlayer);
        return cookiePlayer != null && cookiePlayer.getState() == PlayerState.IN_GAME && game instanceof MicroBattlesGame;
    }

    private boolean isGameRunning(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        Game game = GameManager.getGameOfPlayer(cookiePlayer);
        return game != null && game.hasStarted();
    }

    @Override
    protected boolean shouldAllowBlockBreak(BlockBreakEvent event) {
        return isPlayerInGame(event.getPlayer()) && isGameRunning(event.getPlayer());
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
            return false; // Prevent damage if not in a running MicroBattlesGame
        }

        if (event instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            if (damageByEntityEvent.getDamager() instanceof Player damager) {
                CookiePlayer damagerCookiePlayer = PlayerManager.getPlayer(damager);

                // Prevent damage from spectators or between players of the same team
                return damager.getGameMode() != GameMode.SPECTATOR &&
                        !microBattlesGame.arePlayersInSameTeam(cookiePlayer, damagerCookiePlayer);
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

            // Check if the player was killed by another player
            Player killer = player.getKiller();
            if (killer != null) {
                // Send a message to the killer
                killer.sendMessage("§aYou killed " + player.getName() + "!");
                // play a sound to the killer
                killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1, 1);
            }

            microBattlesGame.handlePlayerDeath(cookiePlayer);
        }
    }


    @EventHandler
    public void onSpectatorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isPlayerInGame(player) && player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }
}