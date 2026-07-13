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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.cookiebuild.cookiedough.dao.GenericDAOImpl;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.lobby.LobbyScoreboard;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.MinigameProgressionId;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.service.MatchService;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.ui.CustomScoreboardManager;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.listener.KitSelectorListener;
import com.cookiebuild.microbattles.map.GameMap;
import com.cookiebuild.microbattles.map.MapManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public class MicroBattlesGame extends Game {
    int teamSize = 3;
    private GameMap map;
    private final HashMap<String, MicroBattlesTeam> teams = new HashMap<>();
    private final Gson gson = new Gson();
    private final Map<UUID, Integer> playerOriginalViewDistances = new HashMap<>();

    // Quick start transition tracking
    private boolean wasInQuickStart = false;
    private int remainingTimeAtQuickStart = 0;
    private int ticksSinceQuickStart = 0;

    private static final int MICROBATTLES_VIEW_DISTANCE = 8;
    private static final int WALL_REMOVE_DELAY_SECONDS = 15;
    private int wallRemoveTimer = 0;
    private boolean wallRemoved = false;
    private int[] wallCoordinates;

    private final CustomScoreboardManager scoreboardManager;

    private EntityManager gameEntityManager;
    private final MatchService matchService;
    private Match currentMatchInstance;
    private final HashMap<UUID, PlayerData> participantPlayerData = new HashMap<>();
    private final HashMap<UUID, Integer> playerKillsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerDeathsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerAssistsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerTeamsEliminatedThisMatch = new HashMap<>();
    private final Map<UUID, PlayerMatchPerformance> matchPerformances = new HashMap<>();

    public MicroBattlesGame() {
        super("MicroBattles");
        setupTeams();

        this.scoreboardManager = new CustomScoreboardManager();
        this.gameEntityManager = HibernateUtil.createEntityManager();
        this.matchService = new MatchService(this.gameEntityManager);

        Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> {
            try {
                String randomMapName = MapManager.getRandomMapName();
                map = MapManager.loadMapForGame(this.getGameId(), randomMapName);
                this.wallCoordinates = MapManager.getWallCoordinatesForMap(randomMapName);
                map.identifyWallBlocks(wallCoordinates);
            } catch (IOException | RuntimeException e) {
                MicroBattles.getInstance().getLogger()
                        .severe("Failed to load map for MicroBattlesGame: " + e.getMessage());
                cleanupMap();
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
            GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
            PlayerData pd = playerDataDAO.findById(player.getPlayer().getUniqueId());
            if (pd != null) {
                participantPlayerData.put(player.getPlayer().getUniqueId(), pd);
            } else {
                MicroBattles.getInstance().getLogger().warning(
                        "Could not find PlayerData for " + player.getPlayer().getName() + " when adding to game.");
                removePlayer(player);
                LobbyManager.teleportPlayerToLobby(player);

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
        super.startGame();

        // Reset quick start tracking when game starts
        wasInQuickStart = false;
        remainingTimeAtQuickStart = 0;
        ticksSinceQuickStart = 0;

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

        for (UUID playerId : participantPlayerData.keySet()) {
            PlayerData playerData = participantPlayerData.get(playerId);
            PlayerMatchPerformance perf = new PlayerMatchPerformance(currentMatchInstance, playerData);
            JsonObject metrics = new JsonObject();
            metrics.addProperty("teamsEliminated", 0);
            perf.setGameSpecificMetrics(metrics.toString());
            matchPerformances.put(playerId, perf);
            currentMatchInstance.addPerformance(perf);
        }

        equipKitsForGameStart();
    }

    private void equipKitsForGameStart() {
        KitManager kitManager = KitManager.getInstance();
        for (CookiePlayer cookiePlayer : getPlayers()) {
            kitManager.equipLastSelectedKit(cookiePlayer.getPlayer());
            giveTeamColoredWool(cookiePlayer);

            String selectedKitName = kitManager.getSelectedKit(cookiePlayer.getPlayer().getUniqueId());
            String kitName = "Default";
            if (selectedKitName != null && !selectedKitName.isEmpty()) {
                kitName = selectedKitName.split(":")[0];
            }

            String originalName = cookiePlayer.getPlayer().getName();
            String kitDisplayName = "§f" + originalName + "\n§7[" + kitName + "]";
            cookiePlayer.getPlayer().setDisplayName(kitDisplayName);
            cookiePlayer.getPlayer().setPlayerListName(kitDisplayName);

            cookiePlayer.getPlayer().sendMessage(
                    "§a" + LocaleManager.getMessage("kit.equipped", cookiePlayer.getPlayer().locale(), kitName));
            cookiePlayer.getPlayer().showTitle(
                    Title.title(
                            Component.text("§6" + kitName),
                            Component.text("§aKit equipped!"),
                            Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1))));
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
        // Track quick start transitions
        if (inQuickStart && !wasInQuickStart) {
            // Just transitioned to quick start - set timer to continue smoothly
            int normalRemainingTime = START_DELAY_SECONDS - getStartTimer();
            remainingTimeAtQuickStart = Math.min(normalRemainingTime, QUICK_START_DELAY_SECONDS);
            wasInQuickStart = true;
            ticksSinceQuickStart = 0;
        } else if (!inQuickStart && wasInQuickStart) {
            // Transitioned out of quick start
            wasInQuickStart = false;
            remainingTimeAtQuickStart = 0;
            ticksSinceQuickStart = 0;
        } else if (inQuickStart && wasInQuickStart) {
            // Continue counting ticks since quick start
            ticksSinceQuickStart++;
        }

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
        for (CookiePlayer player : getPlayers()) {
            Player bukkitPlayer = player.getPlayer();
            String gameStateText = "";
            String countdownInfo = "";

            if (getState() == GameState.OPEN) {
                gameStateText = LocaleManager.getMessage("game.waiting_for_players", bukkitPlayer.locale());
                if (getStartTimer() > 0) {
                    int remainingTime;
                    if (inQuickStart && wasInQuickStart) {
                        // Use smooth transition: countdown from where we left off
                        remainingTime = remainingTimeAtQuickStart - ticksSinceQuickStart;
                    } else if (inQuickStart) {
                        // Normal quick start calculation (first tick of quick start)
                        remainingTime = QUICK_START_DELAY_SECONDS - getStartTimer();
                    } else {
                        // Normal countdown
                        remainingTime = START_DELAY_SECONDS - getStartTimer();
                    }
                    // Ensure remaining time is never negative or zero in display
                    remainingTime = Math.max(1, remainingTime);
                    gameStateText = ""; // No need to show waiting for players when countdown started
                    countdownInfo = LocaleManager.getMessage("game.starting_in", bukkitPlayer.locale(), remainingTime);
                }
            } else if (getState() == GameState.RUNNING) {
                gameStateText = LocaleManager.getMessage("game.running", bukkitPlayer.locale());
                if (!wallRemoved) {
                    countdownInfo = LocaleManager.getMessage("game.wall_drops_in", bukkitPlayer.locale(),
                            WALL_REMOVE_DELAY_SECONDS - wallRemoveTimer);
                }
            } else {
                gameStateText = LocaleManager.getMessage("game.ended", bukkitPlayer.locale());
            }

            bukkitPlayer.sendActionBar(net.kyori.adventure.text.Component.text(gameStateText + " " + countdownInfo));
            scoreboardManager.createScoreboard(bukkitPlayer, "§6§lMicroBattles");
            scoreboardManager.updateScore(bukkitPlayer, "§e", 6);
            scoreboardManager.updateScore(bukkitPlayer, "§fTeams Left: §a" + getActiveTeamsCount(), 5);
            scoreboardManager.updateScore(bukkitPlayer, "§fKills: §a" + getPlayerKills(player), 4);
            scoreboardManager.updateScore(bukkitPlayer, "§e", 3);
            scoreboardManager.updateScore(bukkitPlayer, "§7", 2);
            scoreboardManager.updateScore(bukkitPlayer, "§ewww.cookie-build.com", 1);

            int line = 11;
            for (MicroBattlesTeam team : teams.values()) {
                scoreboardManager.updateScore(bukkitPlayer, team.getName() + ": " + team.getAlivePlayers().size(),
                        line--);
            }
        }
    }

    private int getActiveTeamsCount() {
        return (int) teams.values().stream().filter(
                team -> team.getPlayers().stream().anyMatch(p -> p.getPlayer().getGameMode() != GameMode.SPECTATOR))
                .count();
    }

    private int getKillsThisMatch(UUID playerId) {
        return playerKillsThisMatch.getOrDefault(playerId, 0);
    }

    private int getDeathsThisMatch(UUID playerId) {
        return playerDeathsThisMatch.getOrDefault(playerId, 0);
    }

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
        List<MicroBattlesTeam> remainingTeams = teams.values().stream().filter(team -> team.getPlayerCount() > 0)
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
            try {
                for (Map.Entry<UUID, PlayerData> entry : participantPlayerData.entrySet()) {
                    UUID playerId = entry.getKey();
                    PlayerMatchPerformance perf = matchPerformances.get(playerId);
                    if (perf == null) {
                        MicroBattles.getInstance().getLogger().warning("No performance record found for player "
                                + playerId + " in match " + currentMatchInstance.getId());
                        continue;
                    }
                    perf.setKillsInMatch(playerKillsThisMatch.getOrDefault(playerId, 0));
                    perf.setDeathsInMatch(playerDeathsThisMatch.getOrDefault(playerId, 0));
                    perf.setAssistsInMatch(playerAssistsThisMatch.getOrDefault(playerId, 0));
                    JsonObject metrics = gson.fromJson(perf.getGameSpecificMetrics(), JsonObject.class);
                    if (metrics == null)
                        metrics = new JsonObject();
                    metrics.addProperty("teamsEliminated", playerTeamsEliminatedThisMatch.getOrDefault(playerId, 0));
                    perf.setGameSpecificMetrics(metrics.toString());
                }
                matchService.endMatch(this.currentMatchInstance, winnerPlayerDataList);
                MinigameProgressionService minigameStatsService = new MinigameProgressionService(gameEntityManager);
                for (Map.Entry<UUID, PlayerData> entry : participantPlayerData.entrySet()) {
                    UUID playerId = entry.getKey();

                    EntityTransaction transaction = gameEntityManager.getTransaction();
                    try {
                        transaction.begin();

                        boolean isWinner = winnerPlayerDataList.stream().anyMatch(pd -> pd.getId().equals(playerId));
                        int kills = getKillsThisMatch(playerId);
                        int deaths = getDeathsThisMatch(playerId);
                        int assists = playerAssistsThisMatch.getOrDefault(playerId, 0);

                        int coinsGained = isWinner ? 50 : 10;
                        coinsGained += kills * 5;
                        coinsGained += assists * 2;

                        int xpGained = isWinner ? 100 : 25;
                        xpGained += kills * 10;
                        xpGained += assists * 5;

                        PlayerData playerData = entry.getValue();
                        if (playerData != null) {
                            playerData.addCoins(coinsGained);
                            gameEntityManager.merge(playerData);
                        }

                        MinigameProgressionId id = new MinigameProgressionId(playerId,
                                MinigameProgressionService.MICROBATTLES);
                        com.cookiebuild.cookiedough.model.MinigameProgression stats = gameEntityManager
                                .find(com.cookiebuild.cookiedough.model.MinigameProgression.class, id);

                        int oldLevel = 1;
                        int oldExperience = 0;
                        if (stats == null) {
                            stats = new com.cookiebuild.cookiedough.model.MinigameProgression(playerId,
                                    MinigameProgressionService.MICROBATTLES);
                        } else {
                            oldLevel = stats.getLevel();
                            oldExperience = stats.getExperience();
                        }

                        stats.addExperience(xpGained);
                        gameEntityManager.merge(stats);

                        transaction.commit();

                        // Log the changes for debugging
                        MicroBattles.getInstance().getLogger().info(
                                "Player " + playerId + " - Coins: +" + coinsGained +
                                        ", XP: " + oldExperience + " -> " + stats.getExperience() + " (+" + xpGained
                                        + ")" +
                                        ", Level: " + oldLevel + " -> " + stats.getLevel());

                        // Invalidate lobby scoreboard cache for this player
                        LobbyScoreboard.invalidatePlayerCache(playerId);

                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            // Check if player leveled up
                            if (stats.getLevel() > oldLevel) {
                                player.sendMessage("§6§l✦ LEVEL UP! ✦");
                                player.sendMessage("§eYou reached level §a" + stats.getLevel() + "§e in MicroBattles!");
                                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f,
                                        1.0f);
                            }

                            String rewardMessage;
                            if (isWinner) {
                                rewardMessage = LocaleManager.getMessage("reward.victory", player.locale(),
                                        coinsGained,
                                        xpGained);
                            } else {
                                rewardMessage = LocaleManager.getMessage("reward.defeat", player.locale(),
                                        coinsGained,
                                        xpGained);
                            }
                            player.sendMessage(rewardMessage);
                        }
                    } catch (Exception e) {
                        if (transaction.isActive()) {
                            transaction.rollback();
                        }
                        MicroBattles.getInstance().getLogger()
                                .severe("Failed to save rewards for player " + playerId + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                MicroBattles.getInstance().getLogger().severe("Error during match finalization: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (gameEntityManager.isOpen()) {
                    gameEntityManager.close();
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (CookiePlayer player : getPlayers()) {
                    LobbyManager.teleportPlayerToLobby(player);
                }
                cleanupMap();
                GameManager.removeGame(MicroBattlesGame.this);
            }
        }.runTaskLater(MicroBattles.getInstance(), 20L * 10);
    }

    @Override
    public void removePlayer(CookiePlayer player) {
        super.removePlayer(player);
        for (MicroBattlesTeam team : teams.values()) {
            team.removePlayer(player);
        }

        // Restore view distance
        if (playerOriginalViewDistances.containsKey(player.getPlayer().getUniqueId())) {
            player.getPlayer().setViewDistance(playerOriginalViewDistances.get(player.getPlayer().getUniqueId()));
            playerOriginalViewDistances.remove(player.getPlayer().getUniqueId());
        }

        scoreboardManager.removeScoreboard(player.getPlayer());
        if (getState() == GameState.RUNNING) {
            checkForWinner();
        } else {
            participantPlayerData.remove(player.getPlayer().getUniqueId());
        }
    }

    public boolean arePlayersInSameTeam(CookiePlayer player1, CookiePlayer player2) {
        MicroBattlesTeam team1 = getPlayerTeam(player1);
        MicroBattlesTeam team2 = getPlayerTeam(player2);
        return team1 != null && team1.equals(team2);
    }

    private MicroBattlesTeam getPlayerTeam(CookiePlayer player) {
        return teams.values().stream().filter(t -> t.getPlayers().contains(player)).findFirst().orElse(null);
    }

    public String getPlayerTeamColor(CookiePlayer player) {
        MicroBattlesTeam team = getPlayerTeam(player);
        return team != null ? team.getName() : null;
    }

    public void updatePlayerNameColor(CookiePlayer player) {
        String teamColor = getPlayerTeamColor(player);
        if (teamColor != null) {
            Scoreboard scoreboard = player.getPlayer().getScoreboard();
            Team team = scoreboard.getTeam(teamColor);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamColor);
            }
            team.setPrefix(getColorCode(teamColor));
            team.addEntry(player.getPlayer().getName());
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
        return teamColor != null ? getColorCode(teamColor) + player.getPlayer().getName()
                : player.getPlayer().getName();
    }

    public void handlePlayerFall(CookiePlayer player) {
        if (player.getState() == com.cookiebuild.cookiedough.player.PlayerState.IN_GAME) {
            handlePlayerDeath(player, null);
        }
    }

    public void handlePlayerDeath(CookiePlayer victim, CookiePlayer killer) {
        if (victim.getState() != com.cookiebuild.cookiedough.player.PlayerState.IN_GAME) {
            return;
        }
        victim.setState(com.cookiebuild.cookiedough.player.PlayerState.SPECTATING);
        playerDeathsThisMatch.put(victim.getPlayer().getUniqueId(),
                getDeathsThisMatch(victim.getPlayer().getUniqueId()) + 1);
        if (killer != null) {
            playerKillsThisMatch.put(killer.getPlayer().getUniqueId(),
                    getKillsThisMatch(killer.getPlayer().getUniqueId()) + 1);
            killer.getPlayer().sendMessage("§aYou killed " + getColoredPlayerName(victim));
        }
        victim.getPlayer().setGameMode(GameMode.SPECTATOR);
        victim.getPlayer().sendMessage(LocaleManager.getMessage("game.player_died", victim.getPlayer().locale()));
        checkForWinner();
    }

    public void respawnPlayerToTeamSpawn(CookiePlayer player) {
        Location spawnLocation = map.getTeamSpawn(getTeamNumber(player));
        spawnLocation.setWorld(map.getWorld());
        player.getPlayer().teleport(spawnLocation);
    }

    private String getRomanNumeral(int number) {
        switch (number) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            default:
                return String.valueOf(number);
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
        // Store and set view distance
        playerOriginalViewDistances.put(player.getPlayer().getUniqueId(), player.getPlayer().getViewDistance());
        player.getPlayer().setViewDistance(MICROBATTLES_VIEW_DISTANCE);

        Location spawnLocation = map.getTeamSpawn(getTeamNumber(player));
        spawnLocation.setWorld(map.getWorld());
        player.getPlayer().teleport(spawnLocation);

        if (this.getState() != GameState.RUNNING) {
            player.getPlayer().setGameMode(GameMode.SURVIVAL);
            KitSelectorListener.giveKitSelectorCookie(player.getPlayer());
            player.getPlayer().showTitle(
                    Title.title(
                            Component.text("§6Kit Selection"),
                            Component.text("§aUse the cookie to choose your kit!"),
                            Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            updatePlayerNameColor(player);
        }
    }

    @Override
    public void resetGame() {
        super.resetGame();
        // Reset quick start tracking
        wasInQuickStart = false;
        remainingTimeAtQuickStart = 0;
        ticksSinceQuickStart = 0;
    }

    public void cleanupMap() {
        if (map != null && MapManager.unloadMap(map)) {
            map = null;
        }
    }

    public void shutdown() {
        setState(GameState.FINISHED);
        for (CookiePlayer player : getPlayers()) {
            LobbyManager.teleportPlayerToLobby(player);
        }
        cleanupMap();
        GameManager.removeGame(this);
    }
}
