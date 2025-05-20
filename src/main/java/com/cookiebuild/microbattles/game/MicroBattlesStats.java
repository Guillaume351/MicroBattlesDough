package com.cookiebuild.microbattles.game;

import com.cookiebuild.cookiedough.model.GameStats;
import com.cookiebuild.cookiedough.model.PlayerData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "microbattles_stats")
public class MicroBattlesStats extends GameStats {

    @Column(nullable = false)
    private int teamsEliminated;

    // Constructors
    public MicroBattlesStats() {
        super();
        this.setGameType("MicroBattles");
        this.teamsEliminated = 0;
    }

    public MicroBattlesStats(PlayerData player) {
        super(player, "MicroBattles");
        this.teamsEliminated = 0;
    }

    // Getters and setters for teamsEliminated
    public int getTeamsEliminated() {
        return teamsEliminated;
    }

    public void setTeamsEliminated(int teamsEliminated) {
        this.teamsEliminated = teamsEliminated;
    }

    // Utility methods for teamsEliminated
    public void incrementTeamsEliminated() {
        this.teamsEliminated++;
    }

    public void addTeamsEliminated(int count) {
        this.teamsEliminated += count;
    }

    @Override
    public java.util.Map<String, String> getFormattedSpecificStats() {
        java.util.Map<String, String> specificStats = new java.util.LinkedHashMap<>();
        // getTotalKills() from base GameStats will represent players eliminated in the
        // context of this game.
        specificStats.put("Players Elim.", String.valueOf(this.getTotalKills()));
        specificStats.put("Teams Elim.", String.valueOf(this.getTeamsEliminated()));
        return specificStats;
    }
}