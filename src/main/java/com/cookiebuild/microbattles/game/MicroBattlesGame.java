package com.cookiebuild.microbattles.game;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.ui.CustomScoreboardManager;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.map.GameMap;
import com.cookiebuild.microbattles.map.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashMap;

public class MicroBattlesGame extends Game {
    int teamSize = 3;
    private GameMap map;
    private final HashMap<String, MicroBattlesTeam> teams = new HashMap<>();

    private static final int START_DELAY_SECONDS = 10;

    private final CustomScoreboardManager scoreboardManager;

    public MicroBattlesGame() {
        super("MicroBattles");
        // TODO Ask MapManager for a map. Load it
        setupTeams();

        this.scoreboardManager = new CustomScoreboardManager();

        Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
            // loap map
            try {
                map = MapManager.loadMapForGame(this.getGameId(), "game-1");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void setupTeams() {
        teams.put("Red", new MicroBattlesTeam("Red", teamSize));
        teams.put("Blue", new MicroBattlesTeam("Blue", teamSize));
        teams.put("Yellow", new MicroBattlesTeam("Yellow", teamSize));
        teams.put("Green", new MicroBattlesTeam("Green", teamSize));
    }

    public void assignTeam(CookiePlayer player) {
        int minPlayerAmount = teams.values().stream().mapToInt(MicroBattlesTeam::getPlayerCount).min().orElse(0);
        MicroBattlesTeam assignedTeam = teams.values().stream().filter(team -> team.getPlayerCount() <= minPlayerAmount).findFirst().orElse(null);
        if (assignedTeam == null) {
            throw new IllegalStateException("No team available");
        }
        assignedTeam.addPlayer(player);
    }

    @Override
    public void addPlayer(CookiePlayer player) {
        if (!getPlayers().contains(player)) {
            super.addPlayer(player);
            assignTeam(player);
            teleportToGame(player);
        }
    }

    @Override
    public void registerANewGame() {
        GameManager.addGame(new MicroBattlesGame());
    }

    public int getTeamNumber(CookiePlayer player) {
        // find the team number of the player
        int teamNumber = teams.values().stream().mapToInt(team -> team.getPlayerCount()).min().orElse(0);
        return teamNumber;
    }

    @Override
    protected void teleportToGame(CookiePlayer player) {
        Location spawnLocation = map.getTeamSpawn(getTeamNumber(player));
        World gameWorld = Bukkit.getWorld(this.getGameId().toString());

        if (gameWorld == null) {
            player.getPlayer().sendMessage("Error: The game world is not loaded.");
            return;
        }

        spawnLocation.setWorld(gameWorld);

        // Implement teleportation logic
        player.getPlayer().teleport(spawnLocation);
    }

    @Override
    public boolean isGameEnded() {
        return this.getState() == GameState.FINISHED;
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


    @Override
    public void tick() {
        //super.tick(); TODO: Fix this
        updateGameInfo();
    }

    private void updateGameInfo() {
        String gameState;
        String countdownInfo = "";

        if (getState() == GameState.OPEN) {
            gameState = "Waiting for players";
            if (getStartTimer() > 0) {
                countdownInfo = "Starting in " + (START_DELAY_SECONDS - getStartTimer()) + "s";
            }
        } else if (getState() == GameState.RUNNING) {
            gameState = "Game in progress";
        } else {
            gameState = "Game ended";
        }

        for (CookiePlayer player : getPlayers()) {
            Player bukkitPlayer = player.getPlayer();

            // Update action bar
            bukkitPlayer.sendActionBar(gameState + " " + countdownInfo);

            // Update scoreboard
            scoreboardManager.createScoreboard(bukkitPlayer, "MicroBattles");
            scoreboardManager.updateScore(bukkitPlayer, "Game State:", 15);
            scoreboardManager.updateScore(bukkitPlayer, gameState, 14);
            scoreboardManager.updateScore(bukkitPlayer, "", 13);
            scoreboardManager.updateScore(bukkitPlayer, "Players:", 12);

            int line = 11;
            for (MicroBattlesTeam team : teams.values()) {
                scoreboardManager.updateScore(bukkitPlayer, team.getName() + ": " + team.getPlayerCount(), line--);
            }
        }
    }

}