package com.cookiebuild.microbattles.game;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.microbattles.map.GameMap;

import java.util.HashMap;

public class MicroBattlesGame extends Game {
    int teamSize = 3;
    private GameMap map;
    private final HashMap<String, MicroBattlesTeam> teams = new HashMap<>();

    public MicroBattlesGame() {
        super("MicroBattles");
        teams.put("Red", new MicroBattlesTeam("Red", teamSize));
        teams.put("Blue", new MicroBattlesTeam("Blue", teamSize));
        teams.put("Yellow", new MicroBattlesTeam("Yellow", teamSize));
        teams.put("Green", new MicroBattlesTeam("Green", teamSize));
        // TODO Ask MapManager for a map. Load it
    }

    public void assignTeam(CookiePlayer player) {
        int minPlayerAmount = teams.values().stream().mapToInt(MicroBattlesTeam::getPlayerCount).min().orElse(0);
        MicroBattlesTeam assignedTeam = teams.values().stream().filter(team -> team.getPlayerCount() < minPlayerAmount).findFirst().orElse(null);
        if (assignedTeam == null) {
            throw new IllegalStateException("No team available");
        }
        assignedTeam.addPlayer(player);
    }

    @Override
    public void addPlayer(CookiePlayer player) {
        super.addPlayer(player);
        assignTeam(player);
        teleportToGame(player);
    }

    @Override
    protected void teleportToGame(CookiePlayer player) {
        // Implement teleportation logic
    }

    @Override
    public boolean isGameEnded() {
        return this.getState() == Game.GAME_FINISHED;
    }


    @Override
    public int getPlayerCount() {
        return getPlayers().size();
    }

    @Override
    public boolean addPlayerToAvailableTeam(CookiePlayer player) {
        if (isGameEnded()) return false;
        this.addPlayer(player);
        return true;
    }
}