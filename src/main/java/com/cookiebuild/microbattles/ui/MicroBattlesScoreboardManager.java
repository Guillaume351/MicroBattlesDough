package com.cookiebuild.microbattles.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;

/**
 * A stable, team-backed sidebar. Score entries never change, so updating a
 * value does not create a new scoreboard or leave stale duplicate lines.
 */
public final class MicroBattlesScoreboardManager {
    private static final String OBJECTIVE_NAME = "microbattles";
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();

    public void ensureScoreboard(Player player, Component title) {
        Scoreboard scoreboard = scoreboards.get(player.getUniqueId());
        if (scoreboard != null) {
            Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
            if (objective != null && !objective.displayName().equals(title)) {
                objective.displayName(title);
            }
            if (player.getScoreboard() != scoreboard) {
                player.setScoreboard(scoreboard);
            }
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        scoreboards.put(player.getUniqueId(), scoreboard);
        player.setScoreboard(scoreboard);
    }

    public void updateLine(Player player, int score, Component text) {
        Scoreboard scoreboard = scoreboards.get(player.getUniqueId());
        if (scoreboard == null || score < 0 || score > 15) {
            return;
        }
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            return;
        }

        String teamName = "line_" + score;
        String entry = "\u00a7" + Integer.toHexString(score);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.addEntry(entry);
        }
        if (!team.prefix().equals(text)) {
            team.prefix(text);
        }
        objective.getScore(entry).setScore(score);
    }

    public Scoreboard getScoreboard(Player player) {
        return scoreboards.get(player.getUniqueId());
    }

    public void removeScoreboard(Player player) {
        Scoreboard removed = scoreboards.remove(player.getUniqueId());
        if (removed == null) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null && player.isOnline() && player.getScoreboard() == removed) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    public void clear() {
        scoreboards.clear();
    }
}
