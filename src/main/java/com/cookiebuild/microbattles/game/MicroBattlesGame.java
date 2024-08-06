package com.cookiebuild.microbattles.game;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.ui.CustomScoreboardManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.map.GameMap;
import com.cookiebuild.microbattles.map.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class MicroBattlesGame extends Game {
    int teamSize = 3;
    private GameMap map;
    private final HashMap<String, MicroBattlesTeam> teams = new HashMap<>();

    // store player's kits
    private final HashMap<String, Kit> kits = new HashMap<>();

    private static final int WALL_REMOVE_DELAY_SECONDS = 15;
    private int wallRemoveTimer = 0;
    private boolean wallRemoved = false;
    private int[] wallCoordinates;

    private final CustomScoreboardManager scoreboardManager;

    public MicroBattlesGame() {
        super("MicroBattles");
        setupTeams();

        this.scoreboardManager = new CustomScoreboardManager();

        Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
            try {
                String randomMapName = MapManager.getRandomMapName();
                map = MapManager.loadMapForGame(this.getGameId(), randomMapName);
                this.wallCoordinates = MapManager.getWallCoordinatesForMap(randomMapName);
                World gameWorld = Bukkit.getWorld("game_maps/" + this.getGameId().toString());
                if (gameWorld != null) {
                    map.identifyWallBlocks(gameWorld, wallCoordinates);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void setupTeams() {
        teams.put("Blue", new MicroBattlesTeam("Blue", teamSize));
        teams.put("Red", new MicroBattlesTeam("Red", teamSize));
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
    public boolean addPlayer(CookiePlayer player) {
        if (super.addPlayer(player)) {
            assignTeam(player);
            teleportToGame(player);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void registerANewGame() {
        GameManager.addGame(new MicroBattlesGame());
    }

    public int getTeamNumber(CookiePlayer player) {
        MicroBattlesTeam team = teams.values().stream().filter(t -> t.getPlayers().contains(player)).findFirst().orElse(null);
        return team != null ? teams.values().stream().toList().indexOf(team) : -1;
    }

    @Override
    protected void teleportToGame(CookiePlayer player) {
        Location spawnLocation = map.getTeamSpawn(getTeamNumber(player));
        World gameWorld = Bukkit.getWorld("game_maps/" + this.getGameId().toString());

        if (gameWorld == null) {
            player.getPlayer().sendMessage("Error: The game world is not loaded.");
            return;
        }

        spawnLocation.setWorld(gameWorld);
        player.getPlayer().teleport(spawnLocation);

        if (this.getState() != GameState.RUNNING) {
            Kit kit = KitManager.getInstance().getRandomKit();
            kit.equipPlayer(player.getPlayer());
            // Inform the player about their kit
            player.getPlayer().sendMessage("§aYou have been given the §6" + kit.getName() + " §akit!");
            player.getPlayer().sendTitle("§6" + kit.getName(), "§aKit assigned!", 10, 70, 20);

            // store player's kit
            kits.put(player.getPlayer().getUniqueId().toString(), kit);

            updatePlayerNameColor(player);
        }
    }

    // function to ask which kit the player has
    public Kit getKit(CookiePlayer player) {
        return kits.get(player.getPlayer().getUniqueId().toString());
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
        super.tick();
        updateGameInfo();

        if (getState() == GameState.RUNNING) {
            if (!wallRemoved) {
                wallRemoveTimer++;
                if (wallRemoveTimer >= WALL_REMOVE_DELAY_SECONDS) {
                    removeWall();
                }
            }
            checkForWinner();
        }
    }

    private void updateGameInfo() {
        String gameState;
        String countdownInfo = "";

        if (getState() == GameState.OPEN) {
            gameState = "game.waiting_for_players";
            if (getStartTimer() > 0) {
                gameState = "";
                countdownInfo = "Starting in " + (START_DELAY_SECONDS - getStartTimer()) + "s";
            }
        } else if (getState() == GameState.RUNNING) {
            gameState = "game.running";
            if (!wallRemoved) {
                countdownInfo = "Wall drops in " + (WALL_REMOVE_DELAY_SECONDS - wallRemoveTimer) + "s";
            }
        } else {
            gameState = "game.ended";
        }

        for (CookiePlayer player : getPlayers()) {
            Player bukkitPlayer = player.getPlayer();
            boolean isSpectator = bukkitPlayer.getGameMode() == GameMode.SPECTATOR;

            bukkitPlayer.sendActionBar(LocaleManager.getMessage(gameState, player.getPlayer().locale()) + " " + countdownInfo);

            scoreboardManager.createScoreboard(bukkitPlayer, "MicroBattles");
            scoreboardManager.updateScore(bukkitPlayer, "Game State:", 15);
            scoreboardManager.updateScore(bukkitPlayer, LocaleManager.getMessage(gameState, player.getPlayer().locale()), 14);
            scoreboardManager.updateScore(bukkitPlayer, "", 13);
            scoreboardManager.updateScore(bukkitPlayer, isSpectator ? "Spectating" : "Players:", 12);

            int line = 11;
            for (MicroBattlesTeam team : teams.values()) {
                scoreboardManager.updateScore(bukkitPlayer, team.getName() + ": " + team.getPlayers().stream().filter(p -> p.getPlayer().getGameMode() != GameMode.SPECTATOR).count(), line--);
            }
        }
    }

    private void removeWall() {
        wallRemoved = true;
        map.removeWall();
        for (CookiePlayer player : getPlayers()) {
            player.getPlayer().sendMessage(LocaleManager.getMessage("game.wall_removed", player.getPlayer().locale()));
        }
    }

    private void checkForWinner() {
        List<MicroBattlesTeam> remainingTeams = teams.values().stream()
                .filter(team -> team.getPlayerCount() > 0)
                .filter(team -> team.getPlayers().stream().anyMatch(p -> p.getPlayer().getGameMode() != GameMode.SPECTATOR))
                .collect(Collectors.toList());

        if (remainingTeams.size() == 1) {
            MicroBattlesTeam winningTeam = remainingTeams.get(0);
            endGame(winningTeam);
        } else if (remainingTeams.isEmpty()) {
            endGame(null); // Draw
        }
    }

    private void endGame(MicroBattlesTeam winningTeam) {
        setState(GameState.FINISHED);

        String winMessage;
        if (winningTeam != null) {
            winMessage = "game.win_team";
        } else {
            winMessage = "game.draw";
        }

        for (CookiePlayer player : getPlayers()) {
            player.getPlayer().sendMessage(LocaleManager.getMessage(winMessage, player.getPlayer().locale(), winningTeam != null ? winningTeam.getName() : ""));
            player.getPlayer().sendTitle(LocaleManager.getMessage(winMessage, player.getPlayer().locale(), winningTeam != null ? winningTeam.getName() : ""), null, 20, 40, 20);
        }

        // Start a countdown timer
        int teleportDelay = 10; // 10 seconds delay
        Bukkit.getScheduler().runTaskTimer(MicroBattles.getInstance(), new Runnable() {
            int timeLeft = teleportDelay;

            @Override
            public void run() {
                if (timeLeft > 0) {
                    for (CookiePlayer player : getPlayers()) {
                        player.getPlayer().sendTitle("", LocaleManager.getMessage("game.teleport_countdown", player.getPlayer().locale(), String.valueOf(timeLeft)));
                    }
                    timeLeft--;
                } else {
                    for (CookiePlayer player : getPlayers()) {
                        LobbyManager.teleportPlayerToLobby(player);
                    }
                    GameManager.removeGame(MicroBattlesGame.this);
                    Bukkit.getScheduler().cancelTasks(MicroBattles.getInstance());
                }
            }
        }, 0L, 20L); // Run every second
    }

    @Override
    public void removePlayer(CookiePlayer player) {
        super.removePlayer(player);
        for (MicroBattlesTeam team : teams.values()) {
            team.removePlayer(player);
        }

        scoreboardManager.removeScoreboard(player.getPlayer());
        if (getState() == GameState.RUNNING) {
            checkForWinner();
        }
    }

    public boolean arePlayersInSameTeam(CookiePlayer player1, CookiePlayer player2) {
        MicroBattlesTeam team1 = getPlayerTeam(player1);
        MicroBattlesTeam team2 = getPlayerTeam(player2);
        return team1 != null && team1.equals(team2);
    }

    private MicroBattlesTeam getPlayerTeam(CookiePlayer player) {
        return teams.values().stream()
                .filter(team -> team.getPlayers().contains(player))
                .findFirst()
                .orElse(null);
    }

    public String getPlayerTeamColor(CookiePlayer player) {
        MicroBattlesTeam team = getPlayerTeam(player);
        if (team != null) {
            return team.getName();
        }
        return null;
    }

    public void updatePlayerNameColor(CookiePlayer player) {
        String teamColor = getPlayerTeamColor(player);
        if (teamColor != null) {
            String colorCode = getColorCode(teamColor);
            // Use your scoreboard API to set the player's name color
            // For example:
            // scoreboardAPI.setPlayerNameColor(player.getPlayer(), colorCode + player.getPlayer().getName());
            if (colorCode != null) {
                player.getPlayer().setDisplayName(colorCode + player.getPlayer().getName());
            }
        }
    }

    private String getColorCode(String teamColor) {
        switch (teamColor.toLowerCase()) {
            case "red":
                return "§c";
            case "blue":
                return "§9";
            case "yellow":
                return "§e";
            case "green":
                return "§a";
            default:
                return "§f";
        }
    }

    public String getColoredPlayerName(CookiePlayer player) {
        String teamColor = getPlayerTeamColor(player);
        if (teamColor != null) {
            String colorCode = getColorCode(teamColor);
            return colorCode + player.getPlayer().getName() + "§r";
        }
        return player.getPlayer().getName();
    }

    public void handlePlayerFall(CookiePlayer player) {
        if (getState() != GameState.RUNNING) {
            MicroBattlesTeam team = getPlayerTeam(player);
            if (team != null) {
                Location spawnLocation = map.getTeamSpawn(teams.values().stream().toList().indexOf(team));
                World gameWorld = Bukkit.getWorld("game_maps/" + this.getGameId().toString());
                if (gameWorld != null) {
                    spawnLocation.setWorld(gameWorld);
                    player.getPlayer().teleport(spawnLocation);
                }
            }
        }
    }

    public void handlePlayerDeath(CookiePlayer player) {
        if (getState() == GameState.RUNNING) {
            player.getPlayer().setGameMode(GameMode.SPECTATOR);
            // Send message to tell the player they died and are spectating
            player.getPlayer().sendMessage(LocaleManager.getMessage("game.player_died", player.getPlayer().locale()));
            player.getPlayer().sendTitle(LocaleManager.getMessage("game.now_spectating", player.getPlayer().locale()), null, 20, 40, 20);

            MicroBattlesTeam team = getPlayerTeam(player);
            if (team != null) {
                team.removePlayer(player);
                Location spawnLocation = map.getTeamSpawn(teams.values().stream().toList().indexOf(team));
                World gameWorld = Bukkit.getWorld("game_maps/" + this.getGameId().toString());
                if (gameWorld != null) {
                    spawnLocation.setWorld(gameWorld);
                    player.getPlayer().teleport(spawnLocation);
                }
            }

            checkForWinner();
        }
    }

    public void respawnPlayerToTeamSpawn(CookiePlayer player) {
        Location spawnLocation = map.getTeamSpawn(getTeamNumber(player));
        player.getPlayer().teleport(spawnLocation);
    }
}