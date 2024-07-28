package com.cookiebuild.microbattles.game;

import com.cookiebuild.cookiedough.player.CookiePlayer;

import java.util.ArrayList;

public class MicroBattlesTeam {
    private final String name;
    private final int teamSize;

    private final ArrayList<CookiePlayer> players = new ArrayList<>();

    public MicroBattlesTeam(String name, int teamSize) {
        this.name = name;
        this.teamSize = teamSize;
    }

    public boolean addPlayer(CookiePlayer player) {
        if (players.size() >= teamSize) {
            return false;
        }
        players.add(player);
        return true;
    }

    public void removePlayer(CookiePlayer player) {
        players.remove(player);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public ArrayList<CookiePlayer> getPlayers() {
        return players;
    }

    public String getName() {
        return name;
    }

}
