package com.cookiebuild.microbattles.game;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.dao.GenericDAOImpl;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.service.MatchService;
import com.cookiebuild.cookiedough.ui.CustomScoreboardManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.map.GameMap;
import com.cookiebuild.microbattles.map.MapManager;

import jakarta.persistence.EntityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

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

    // --- New Stats and Match Tracking Fields ---
    private EntityManager gameEntityManager;
    private final MatchService matchService;
    private Match currentMatchInstance;
    private final HashMap<UUID, PlayerData> participantPlayerData = new HashMap<>();
    private final HashMap<UUID, Integer> playerKillsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerDeathsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerAssistsThisMatch = new HashMap<>(); // Basic assist tracking
    private final Map<UUID, MicroBattlesMatchPerformance> matchPerformances = new HashMap<>();
    // --- End New Stats and Match Tracking Fields ---

    public MicroBattlesGame() {
        super("MicroBattles");
        setupTeams();

        this.scoreboardManager = new CustomScoreboardManager();

        // Initialize EntityManager and services
        this.gameEntityManager = CookieDough.sessionFactory.createEntityManager();
        this.matchService = new MatchService(this.gameEntityManager);

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
                MicroBattles.getInstance().getLogger()
                        .severe("Failed to load map for MicroBattlesGame: " + e.getMessage());
                GameManager.removeGame(this);
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
        MicroBattlesTeam assignedTeam = teams.values().stream().filter(team -> team.getPlayerCount() <= minPlayerAmount)
                .findFirst().orElse(null);
        if (assignedTeam == null) {
            throw new IllegalStateException("No team available");
        }
        assignedTeam.addPlayer(player);
    }

    @Override
    public boolean addPlayer(CookiePlayer player) {
        if (super.addPlayer(player)) {
            // Fetch and store PlayerData when player successfully joins *before* game start
            GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
            PlayerData pd = playerDataDAO.findById(player.getPlayer().getUniqueId());
            if (pd != null) {
                participantPlayerData.put(player.getPlayer().getUniqueId(), pd);
            } else {
                MicroBattles.getInstance().getLogger().warning(
                        "Could not find PlayerData for " + player.getPlayer().getName() + " when adding to game.");
                // Decide if player can join without PlayerData. For stats, probably not.
                super.removePlayer(player); // Rollback adding player
                return false;
            }
            assignTeam(player);
            teleportToGame(player);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void startGame() {
        super.startGame(); // Call the base game's startGame logic first

        if (!participantPlayerData.isEmpty()) {
            this.currentMatchInstance = matchService.startMatch("MicroBattles",
                    new ArrayList<>(participantPlayerData.values()));
            if (this.currentMatchInstance != null) {
                MicroBattles.getInstance().getLogger()
                        .info("MicroBattles match started: " + this.currentMatchInstance.getId());
            } else {
                MicroBattles.getInstance().getLogger().severe("Failed to start MicroBattles match instance.");
            }
        } else {
            MicroBattles.getInstance().getLogger()
                    .warning("MicroBattles game starting with no participant PlayerData recorded. Match not started.");
        }

        // Initialize performance tracking for all players
        for (UUID playerId : participantPlayerData.keySet()) {
            PlayerData playerData = participantPlayerData.get(playerId);
            MicroBattlesMatchPerformance perf = new MicroBattlesMatchPerformance(currentMatchInstance, playerData);
            matchPerformances.put(playerId, perf);
            currentMatchInstance.addPerformance(perf);
        }
    }

    @Override
    public void registerANewGame() {
        GameManager.addGame(new MicroBattlesGame());
    }

    public int getTeamNumber(CookiePlayer player) {
        MicroBattlesTeam team = teams.values().stream().filter(t -> t.getPlayers().contains(player)).findFirst()
                .orElse(null);
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
            player.getPlayer().setGameMode(GameMode.SURVIVAL);
            Kit kit = KitManager.getInstance().getRandomKit();
            kit.equipPlayer(player.getPlayer());
            // Inform the player about their kit
            player.getPlayer().sendMessage("§aYou have been given the §6" + kit.getName() + " §akit!");
            player.getPlayer().showTitle(
                    Title.title(
                            Component.text("§6" + kit.getName()),
                            Component.text("§aKit assigned!"),
                            Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1))));

            // store player's kit
            kits.put(player.getPlayer().getUniqueId().toString(), kit);

            // Give 16 wools of the color of the team to the player
            giveTeamColoredWool(player);

            updatePlayerNameColor(player);
        }
    }

    private void giveTeamColoredWool(CookiePlayer player) {
        String teamColor = getPlayerTeamColor(player);
        if (teamColor != null) {
            Material woolMaterial = getWoolMaterial(teamColor);
            ItemStack wool = new ItemStack(woolMaterial, 16);
            player.getPlayer().getInventory().addItem(wool);
        }
    }

    private Material getWoolMaterial(String teamColor) {
        switch (teamColor.toLowerCase()) {
            case "red":
                return Material.RED_WOOL;
            case "blue":
                return Material.BLUE_WOOL;
            case "yellow":
                return Material.YELLOW_WOOL;
            case "green":
                return Material.LIME_WOOL;
            default:
                return Material.WHITE_WOOL;
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
        if (isGameEnded())
            return false;
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

        String finalState = gameState;
        String finalCountdownInfo = countdownInfo;

        for (CookiePlayer player : getPlayers()) {
            Player bukkitPlayer = player.getPlayer();

            bukkitPlayer.sendActionBar(Component.text(
                    LocaleManager.getMessage(finalState, player.getPlayer().locale()) + " " + finalCountdownInfo));

            scoreboardManager.createScoreboard(bukkitPlayer, "§6§lMicroBattles");
            scoreboardManager.updateScore(bukkitPlayer, "§e", 6);
            scoreboardManager.updateScore(bukkitPlayer, "§fTeams Left: §a" + getActiveTeamsCount(), 5);
            scoreboardManager.updateScore(bukkitPlayer, "§fKills: §a" + getPlayerKills(player), 4);
            scoreboardManager.updateScore(bukkitPlayer, "§e", 3);
            scoreboardManager.updateScore(bukkitPlayer, "§7", 2);
            scoreboardManager.updateScore(bukkitPlayer, "§ewww.cookie-build.com", 1);

            int line = 11;
            for (MicroBattlesTeam team : teams.values()) {
                scoreboardManager.updateScore(bukkitPlayer,
                        team.getName() + ": " + team.getAlivePlayers().size(),
                        line--);
            }
        }
    }

    private int getActiveTeamsCount() {
        return (int) teams.values().stream()
                .filter(team -> team.getPlayers().stream()
                        .anyMatch(p -> p.getPlayer().getGameMode() != GameMode.SPECTATOR))
                .count();
    }

    private int getKillsThisMatch(UUID playerId) {
        return playerKillsThisMatch.getOrDefault(playerId, 0);
    }

    private int getDeathsThisMatch(UUID playerId) {
        return playerDeathsThisMatch.getOrDefault(playerId, 0);
    }

    // Updated getPlayerKills for scoreboard to use this match's kills
    private int getPlayerKills(CookiePlayer player) {
        return getKillsThisMatch(player.getPlayer().getUniqueId());
    }

    private void recordAssist(CookiePlayer assister) {
        if (assister == null)
            return;
        playerAssistsThisMatch.put(assister.getPlayer().getUniqueId(),
                playerAssistsThisMatch.getOrDefault(assister.getPlayer().getUniqueId(), 0) + 1);
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
                .filter(team -> team.getPlayers().stream()
                        .anyMatch(p -> p.getPlayer().getGameMode() != GameMode.SPECTATOR))
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

        List<PlayerData> winnerPlayerDataList = new ArrayList<>();
        if (winningTeam != null) {
            for (CookiePlayer winner : winningTeam.getPlayers()) {
                PlayerData pd = participantPlayerData.get(winner.getPlayer().getUniqueId());
                if (pd != null) {
                    winnerPlayerDataList.add(pd);
                }
            }
        }

        if (this.currentMatchInstance != null) {
            matchService.endMatch(this.currentMatchInstance, winnerPlayerDataList);

            // Record final performance stats for all participants
            for (Map.Entry<UUID, PlayerData> entry : participantPlayerData.entrySet()) {
                UUID playerId = entry.getKey();
                PlayerData playerData = entry.getValue();
                MicroBattlesMatchPerformance perf = matchPerformances.get(playerId);

                Map<String, Object> specificMetrics = new HashMap<>();
                specificMetrics.put("teamsEliminated", perf.getTeamsEliminated());
                specificMetrics.put("assists", perf.getAssists());

                matchService.recordPlayerPerformance(
                        currentMatchInstance,
                        playerData,
                        perf.getKillsInMatch(),
                        perf.getDeathsInMatch(),
                        perf.getAssists(),
                        specificMetrics);
            }
        } else {
            MicroBattles.getInstance().getLogger()
                    .warning("currentMatchInstance was null during endGame for MicroBattles.");
        }

        String winMessage;
        if (winningTeam != null) {
            winMessage = "game.win_team";
        } else {
            winMessage = "game.draw";
        }

        String finalWinMessage = winMessage;
        String teamName = winningTeam != null ? winningTeam.getName() : "";

        for (CookiePlayer player : getPlayers()) {
            Player bukkitPlayer = player.getPlayer();
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                bukkitPlayer.sendMessage(LocaleManager.getMessage(finalWinMessage, bukkitPlayer.locale(), teamName));
                bukkitPlayer.showTitle(
                        Title.title(
                                Component.text(
                                        LocaleManager.getMessage(finalWinMessage, bukkitPlayer.locale(), teamName)),
                                Component.empty(),
                                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(2),
                                        Duration.ofSeconds(1))));
            }
        }

        int teleportDelay = 10;
        new BukkitRunnable() {
            int timeLeft = teleportDelay;

            @Override
            public void run() {
                if (timeLeft > 0) {
                    for (UUID playerId : participantPlayerData.keySet()) {
                        Player p = Bukkit.getPlayer(playerId);
                        if (p != null && p.isOnline()) {
                            p.showTitle(
                                    Title.title(
                                            Component.empty(),
                                            Component.text(LocaleManager.getMessage(
                                                    "game.teleport_countdown",
                                                    p.locale(),
                                                    String.valueOf(timeLeft))),
                                            Title.Times.times(Duration.ofSeconds(0), Duration.ofSeconds(1),
                                                    Duration.ofSeconds(0))));
                        }
                    }
                    timeLeft--;
                } else {
                    for (UUID playerId : participantPlayerData.keySet()) {
                        Player p = Bukkit.getPlayer(playerId);
                        if (p != null && p.isOnline()) {
                            CookiePlayer cp = PlayerManager.getPlayer(p);
                            if (cp != null)
                                LobbyManager.teleportPlayerToLobby(cp);
                        }
                    }
                    GameManager.removeGame(MicroBattlesGame.this);
                    if (gameEntityManager != null && gameEntityManager.isOpen()) {
                        gameEntityManager.close();
                        MicroBattles.getInstance().getLogger()
                                .info("GameEntityManager closed for MicroBattles game: " + getGameId());
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer(MicroBattles.getInstance(), 0L, 20L);
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
            if (colorCode != null) {
                Player bukkitPlayer = player.getPlayer();
                String coloredName = colorCode + bukkitPlayer.getName();

                // Update display name (affects chat)
                bukkitPlayer.customName(Component.text(coloredName));
                bukkitPlayer.setCustomNameVisible(true);

                // Update scoreboard team (affects nametag)
                Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                Team team = scoreboard.getEntryTeam(bukkitPlayer.getName());

                if (team == null) {
                    team = scoreboard.registerNewTeam(bukkitPlayer.getName());
                }

                // Set team prefix to color code
                team.prefix(Component.text(colorCode));

                // Add player to the team
                team.addEntry(bukkitPlayer.getName());

                // Update player's nametag visibility
                bukkitPlayer.setScoreboard(scoreboard);
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

    public void handlePlayerDeath(CookiePlayer victim, CookiePlayer killer) {
        if (getState() != GameState.RUNNING)
            return;

        UUID victimId = victim.getPlayer().getUniqueId();
        MicroBattlesMatchPerformance victimPerf = matchPerformances.get(victimId);
        victimPerf.incrementDeathsInMatch();

        if (killer != null && !arePlayersInSameTeam(victim, killer)) {
            UUID killerId = killer.getPlayer().getUniqueId();
            MicroBattlesMatchPerformance killerPerf = matchPerformances.get(killerId);
            killerPerf.incrementKillsInMatch();

            // Check if this kill eliminated the team
            MicroBattlesTeam victimTeam = getPlayerTeam(victim);
            if (victimTeam != null && victimTeam.getAlivePlayers().isEmpty()) {
                killerPerf.incrementTeamsEliminated();
            }

            killer.getPlayer().sendMessage(Component.text("You eliminated ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(getColoredPlayerName(victim)))
                    .append(Component.text("!")));
        }

        victim.getPlayer().setGameMode(GameMode.SPECTATOR);
        victim.getPlayer().sendMessage(LocaleManager.getMessage("game.player_died", victim.getPlayer().locale()));
        victim.getPlayer().showTitle(
                Title.title(
                        Component.text(LocaleManager.getMessage("game.now_spectating", victim.getPlayer().locale())),
                        Component.empty(),
                        Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1))));

        MicroBattlesTeam team = getPlayerTeam(victim);
        if (team != null) {
            Location spawnLocation = map.getTeamSpawn(teams.values().stream().toList().indexOf(team));
            World gameWorld = Bukkit.getWorld("game_maps/" + this.getGameId().toString());
            if (gameWorld != null) {
                spawnLocation.setWorld(gameWorld);
                victim.getPlayer().teleport(spawnLocation);
            }
        }
        checkForWinner();
    }

    public void respawnPlayerToTeamSpawn(CookiePlayer player) {
        Location spawnLocation = map.getTeamSpawn(getTeamNumber(player));
        player.getPlayer().teleport(spawnLocation);
    }
}