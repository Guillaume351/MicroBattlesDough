package com.cookiebuild.microbattles.game;

import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "microbattles_match_performances")
public class MicroBattlesMatchPerformance extends PlayerMatchPerformance {

    @Column(nullable = false)
    private int teamsEliminated;

    @Column(nullable = false)
    private int assists;

    public MicroBattlesMatchPerformance() {
        super();
    }

    public MicroBattlesMatchPerformance(Match match, PlayerData player) {
        super(match, player);
        this.teamsEliminated = 0;
        this.assists = 0;
    }

    public int getTeamsEliminated() {
        return teamsEliminated;
    }

    public int getAssists() {
        return assists;
    }

    public void incrementTeamsEliminated() {
        this.teamsEliminated++;
    }

    public void incrementKillsInMatch() {
        setKillsInMatch(getKillsInMatch() + 1);
    }

    public void incrementDeathsInMatch() {
        setDeathsInMatch(getDeathsInMatch() + 1);
    }

    public void incrementAssists() {
        this.assists++;
    }

}
