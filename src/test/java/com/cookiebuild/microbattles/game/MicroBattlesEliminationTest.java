package com.cookiebuild.microbattles.game;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class MicroBattlesEliminationTest {
    @Test
    void eliminationReturnsTheNewSpectatorToSafetyExactlyOnce() throws Exception {
        MicroBattlesGame game = mock(MicroBattlesGame.class);
        doCallRealMethod().when(game).handlePlayerDeath(any(), any());
        when(game.getState()).thenReturn(GameState.RUNNING);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.locale()).thenReturn(Locale.ENGLISH);
        CookiePlayer victim = mock(CookiePlayer.class);
        when(victim.getPlayer()).thenReturn(player);
        AtomicReference<PlayerState> state = new AtomicReference<>(PlayerState.IN_GAME);
        when(victim.getState()).thenAnswer(ignored -> state.get());
        when(player.getGameMode()).thenAnswer(ignored -> state.get() == PlayerState.SPECTATING
                ? GameMode.SPECTATOR : GameMode.SURVIVAL);
        doAnswer(call -> { state.set(call.getArgument(0)); return null; })
                .when(victim).setState(any());

        var teams = new LinkedHashMap<String, MicroBattlesTeam>();
        // Two opposing teams remain in play after the victim falls.
        for (String name : new String[] {"Blue", "Red"}) {
            MicroBattlesTeam team = new MicroBattlesTeam(name, 3);
            CookiePlayer survivor = mock(CookiePlayer.class);
            when(survivor.getPlayer()).thenReturn(mock(Player.class));
            team.addPlayer(survivor);
            teams.put(name, team);
        }
        MicroBattlesTeam victimTeam = new MicroBattlesTeam("Green", 3);
        victimTeam.addPlayer(victim);
        teams.put("Green", victimTeam);
        HashMap<UUID, Integer> deaths = new HashMap<>();
        field(game, "teams", teams);
        field(game, "recentAttackers", new HashMap<UUID, Map<UUID, Long>>());
        field(game, "playerDeathsThisMatch", deaths);

        try (var messages = mockStatic(LocaleManager.class)) {
            messages.when(() -> LocaleManager.getMessage("game.player_died", Locale.ENGLISH))
                    .thenReturn("You died!");
            game.handlePlayerDeath(victim, null);
            game.handlePlayerDeath(victim, null);
        }

        assertEquals(PlayerState.SPECTATING, state.get());
        assertEquals(1, deaths.get(playerId));
        verify(player).setGameMode(GameMode.SPECTATOR);
        verify(game).respawnPlayerToSpectatorSpawn(victim);
        verify(game, never()).setState(GameState.FINISHED);
    }

    private static void field(MicroBattlesGame game, String name, Object value) throws Exception {
        var field = MicroBattlesGame.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(game, value);
    }
}
