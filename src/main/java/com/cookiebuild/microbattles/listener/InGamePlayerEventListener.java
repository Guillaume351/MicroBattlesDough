package com.cookiebuild.microbattles.listener;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.BaseEventBlocker;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class InGamePlayerEventListener extends BaseEventBlocker {

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
        // Allow block breaking only if the player is in a running game
        return isPlayerInGame(event.getPlayer()) && isGameRunning(event.getPlayer());
    }

    @Override
    protected boolean shouldAllowBlockPlace(BlockPlaceEvent event) {
        // Allow block placing only if the player is in a running game
        return isPlayerInGame(event.getPlayer()) && isGameRunning(event.getPlayer());
    }

    @Override
    protected boolean shouldAllowPlayerInteract(PlayerInteractEvent event) {
        // Allow player interaction if the player is in a game (running or not)
        return isPlayerInGame(event.getPlayer());
    }

    @Override
    protected boolean shouldAllowEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return true; // Allow damage to non-player entities
        }
        // Allow damage only if the player is in a running game
        return isPlayerInGame(player) && isGameRunning(player);
    }

    @Override
    protected boolean shouldAllowProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return true; // Allow projectiles from non-player sources
        }
        // Allow projectile launch only if the player is in a running game
        return isPlayerInGame(player) && isGameRunning(player);
    }

    @Override
    protected boolean shouldAllowPlayerDropItem(PlayerDropItemEvent event) {
        // Prevent item dropping in all cases to avoid item loss
        return false;
    }
}