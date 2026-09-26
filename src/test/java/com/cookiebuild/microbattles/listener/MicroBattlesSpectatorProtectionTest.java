package com.cookiebuild.microbattles.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.microbattles.game.MicroBattlesGame;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MicroBattlesSpectatorProtectionTest {
    @ParameterizedTest
    @EnumSource(value = PlayerState.class, names = {"QUEUED", "IN_GAME", "SPECTATING"})
    void onlyActiveParticipantsCanTakeDamage(PlayerState state) {
        Player player = mock(Player.class);
        CookiePlayer participant = mock(CookiePlayer.class);
        when(participant.getState()).thenReturn(state);
        when(player.getGameMode()).thenReturn(
                state == PlayerState.SPECTATING ? GameMode.SPECTATOR : GameMode.SURVIVAL);
        MicroBattlesGame game = mock(MicroBattlesGame.class);
        when(game.hasStarted()).thenReturn(true);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(player);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.VOID);

        try (var players = mockStatic(PlayerManager.class);
             var games = mockStatic(GameManager.class)) {
            players.when(() -> PlayerManager.getPlayer(player)).thenReturn(participant);
            games.when(() -> GameManager.getGameOfPlayer(participant)).thenReturn(game);
            assertEquals(state == PlayerState.IN_GAME,
                    new InGamePlayerEventListener().shouldAllowEntityDamage(damage));
        }
    }

    @ParameterizedTest
    @EnumSource(value = PlayerState.class, names = {"QUEUED", "IN_GAME", "SPECTATING"})
    void fallingSpectatorsReturnAboveTheArenaWithoutAnotherElimination(PlayerState state) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("microbattles-test");
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, -1, 0));
        CookiePlayer participant = mock(CookiePlayer.class);
        when(participant.getState()).thenReturn(state);
        MicroBattlesGame game = mock(MicroBattlesGame.class);
        when(game.hasStarted()).thenReturn(state != PlayerState.QUEUED);
        PlayerMoveEvent move = mock(PlayerMoveEvent.class);
        when(move.getPlayer()).thenReturn(player);
        InGamePlayerEventListener listener = new InGamePlayerEventListener();
        listener.addProtectedWorld(world.getName());

        try (var players = mockStatic(PlayerManager.class);
             var games = mockStatic(GameManager.class)) {
            players.when(() -> PlayerManager.getPlayer(player)).thenReturn(participant);
            games.when(() -> GameManager.getGameOfPlayer(participant)).thenReturn(game);
            listener.onPlayerMove(move);
        }

        verify(game, times(state == PlayerState.SPECTATING ? 1 : 0))
                .respawnPlayerToSpectatorSpawn(participant);
        verify(game, times(state == PlayerState.IN_GAME ? 1 : 0)).handlePlayerFall(participant);
        verify(game, times(state == PlayerState.QUEUED ? 1 : 0)).respawnPlayerToTeamSpawn(participant);
    }

    @ParameterizedTest
    @EnumSource(value = PlayerState.class, names = {"QUEUED", "SPECTATING"})
    void deathCallbacksOutsideActivePlayDoNotRepeatTheElimination(PlayerState state) {
        Player player = mock(Player.class);
        CookiePlayer participant = mock(CookiePlayer.class);
        when(participant.getState()).thenReturn(state);
        MicroBattlesGame game = mock(MicroBattlesGame.class);
        when(game.hasStarted()).thenReturn(true);
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);
        try (var players = mockStatic(PlayerManager.class);
             var games = mockStatic(GameManager.class)) {
            players.when(() -> PlayerManager.getPlayer(player)).thenReturn(participant);
            games.when(() -> GameManager.getGameOfPlayer(participant)).thenReturn(game);
            new InGamePlayerEventListener().onPlayerDeath(death);
        }
        verify(game, never()).handlePlayerDeath(any(), any());
        verify(player, never()).setHealth(anyDouble());
    }
}
