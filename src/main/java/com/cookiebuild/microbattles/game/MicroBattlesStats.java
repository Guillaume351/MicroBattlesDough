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
    private int flagsCaptured;

    @Column(nullable = false)
    private int powerupsCollected;

    @Column(nullable = false)
    private int teamsEliminated;

    // Constructors
    public MicroBattlesStats() {
        super();
        this.setGameType("MicroBattles");
        this.flagsCaptured = 0;
        this.powerupsCollected = 0;
        this.teamsEliminated = 0;
    }

    public MicroBattlesStats(PlayerData player) {
        super(player, "MicroBattles");
        this.flagsCaptured = 0;
        this.powerupsCollected = 0;
        this.teamsEliminated = 0;
    }

    // Getters and setters
    public int getFlagsCaptured() {
        return flagsCaptured;
    }

    public void setFlagsCaptured(int flagsCaptured) {
        this.flagsCaptured = flagsCaptured;
    }

    public int getPowerupsCollected() {
        return powerupsCollected;
    }

    public void setPowerupsCollected(int powerupsCollected) {
        this.powerupsCollected = powerupsCollected;
    }

    public int getTeamsEliminated() {
        return teamsEliminated;
    }

    public void setTeamsEliminated(int teamsEliminated) {
        this.teamsEliminated = teamsEliminated;
    }

    // Utility methods
    public void incrementFlagsCaptured() {
        this.flagsCaptured++;
    }

    public void incrementPowerupsCollected() {
        this.powerupsCollected++;
    }

    public void incrementTeamsEliminated() {
        this.teamsEliminated++;
    }

    public void addFlagsCaptured(int count) {
        this.flagsCaptured += count;
    }

    public void addPowerupsCollected(int count) {
        this.powerupsCollected += count;
    }

    public void addTeamsEliminated(int count) {
        this.teamsEliminated += count;
    }
}