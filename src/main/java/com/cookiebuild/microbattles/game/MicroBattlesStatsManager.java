package com.cookiebuild.microbattles.game;

import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.service.PlayerStatsService;

import jakarta.persistence.EntityManager;

/**
 * Utility class for managing MicroBattles player statistics
 */
public class MicroBattlesStatsManager {

    private final PlayerStatsService statsService;

    public MicroBattlesStatsManager(EntityManager entityManager) {
        this.statsService = new PlayerStatsService(entityManager);
    }

    /**
     * Get or create MicroBattles stats for a player
     * 
     * @param player The player
     * @return The player's MicroBattles stats
     */
    public MicroBattlesStats getOrCreateStats(PlayerData player) {
        return statsService.getOrCreatePlayerStats(player, "MicroBattles", MicroBattlesStats.class);
    }

    /**
     * Update player stats after a MicroBattles game
     * 
     * @param player            The player
     * @param won               Whether the player won
     * @param kills             Number of kills
     * @param deaths            Number of deaths
     * @param teamsEliminated   Number of teams eliminated
     * @return The updated stats
     */
    public MicroBattlesStats updateStatsAfterGame(
            PlayerData player,
            boolean won,
            int kills,
            int deaths,
            int teamsEliminated) {

        MicroBattlesStats stats = statsService.updatePlayerStatsAfterGame(
                player, "MicroBattles", won, kills, deaths, MicroBattlesStats.class);


        stats.addTeamsEliminated(teamsEliminated);

        statsService.saveStats(stats);

        return stats;
    }
}