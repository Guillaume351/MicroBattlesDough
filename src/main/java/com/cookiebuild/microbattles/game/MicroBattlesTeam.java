package com.cookiebuild.microbattles.game;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.GameMode;

import com.cookiebuild.cookiedough.player.CookiePlayer;

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

    public ArrayList<CookiePlayer> getAlivePlayers() {
        return players.stream()
                .filter(p -> p.getPlayer().getGameMode() != GameMode.SPECTATOR)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Set<UUID> getPlayersUUIDs() {
        return players.stream()
                .map(p -> p.getPlayer().getUniqueId())
                .collect(Collectors.toSet());
    }

    public String getName() {
        return name;
    }
}
