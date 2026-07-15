package com.cookiebuild.microbattles.game;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.cookiebuild.cookiedough.dao.GenericDAOImpl;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.lobby.LobbyScoreboard;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.service.MatchService;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.listener.KitSelectorListener;
import com.cookiebuild.microbattles.map.GameMap;
import com.cookiebuild.microbattles.map.MapManager;
import com.cookiebuild.microbattles.ui.MicroBattlesScoreboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;

public class MicroBattlesGame extends Game {
    int teamSize = 3;
    private GameMap map;
    private final Map<String, MicroBattlesTeam> teams = new LinkedHashMap<>();
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

    private final MicroBattlesScoreboardManager scoreboardManager;
    private static final long ASSIST_WINDOW_MILLIS = 10_000L;
    private final Map<UUID, Map<UUID, Long>> recentAttackers = new HashMap<>();
    private BukkitTask cleanupTask;
    private boolean cleanupStarted;

    private final MatchService matchService;
    private Match currentMatchInstance;
    private final HashMap<UUID, PlayerData> participantPlayerData = new HashMap<>();
    private final HashMap<UUID, Integer> playerKillsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerDeathsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerAssistsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerTeamsEliminatedThisMatch = new HashMap<>();

    public MicroBattlesGame() {
        super("MicroBattles");
        setupTeams();

        setCapacity(teamSize * 4);
        this.scoreboardManager = new MicroBattlesScoreboardManager();
        this.matchService = new MatchService(null);

        try {
            String randomMapName = MapManager.getRandomMapName();
            map = MapManager.loadMapForGame(this.getGameId(), randomMapName);
            this.wallCoordinates = MapManager.getWallCoordinatesForMap(randomMapName);
            map.identifyWallBlocks(wallCoordinates);
        } catch (IOException | RuntimeException error) {
            cleanupMap();
            throw new IllegalStateException("MicroBattles map preparation failed: " + error.getMessage(), error);
        }
    }

    public void setupTeams() {
        teams.put("Blue", new MicroBattlesTeam("Blue", teamSize));
        teams.put("Red", new MicroBattlesTeam("Red", teamSize));
        teams.put("Yellow", new MicroBattlesTeam("Yellow", teamSize));
        teams.put("Green", new MicroBattlesTeam("Green", teamSize));
    }

    private MicroBattlesTeam assignTeam(CookiePlayer player) {
        var partyManager = com.cookiebuild.cookiedough.CookieDough.getInstance().getPartyManager();
        UUID playerId = player.getPlayer().getUniqueId();
        List<UUID> onlineParty = partyManager.getMembers(playerId).stream()
                .filter(memberId -> {
                    Player member = Bukkit.getPlayer(memberId);
                    return member != null && member.isOnline();
                })
                .toList();
        if (!onlineParty.isEmpty()) {
            if (onlineParty.size() > teamSize) {
                player.getPlayer().sendMessage("§cYour online party is larger than a MicroBattles team.");
                return null;
            }
            MicroBattlesTeam partyTeam = teams.values().stream()
                    .filter(team -> team.getPlayers().stream().anyMatch(teammate ->
                            partyManager.arePartyMembers(playerId, teammate.getPlayer().getUniqueId())))
                    .findFirst().orElse(null);
            if (partyTeam != null) {
                return partyTeam.addPlayer(player) ? partyTeam : null;
            }
            return teams.values().stream()
                    .filter(team -> team.getPlayerCount() + onlineParty.size() <= teamSize)
                    .min(java.util.Comparator.comparingInt(MicroBattlesTeam::getPlayerCount))
                    .filter(team -> team.addPlayer(player))
                    .orElse(null);
        }
        return teams.values().stream()
                .filter(team -> team.getPlayerCount() < teamSize)
                .min(java.util.Comparator.comparingInt(MicroBattlesTeam::getPlayerCount))
                .filter(team -> team.addPlayer(player))
                .orElse(null);
    }

    @Override
    public synchronized boolean addPlayer(CookiePlayer player) {
        if (player == null || map == null || getState() != GameState.OPEN || getPlayers().contains(player)
                || getPlayers().size() >= getCapacity() || GameManager.getGameOfPlayer(player) != null) {
            return false;
        }

        if (super.addPlayer(player)) {
            GenericDAOImpl<PlayerData> playerDataDAO = new GenericDAOImpl<>(PlayerData.class);
            PlayerData pd = playerDataDAO.findById(player.getPlayer().getUniqueId());
            if (pd != null) {
                participantPlayerData.put(player.getPlayer().getUniqueId(), pd);
            } else {
                MicroBattles.getInstance().getLogger().warning(
                        "Could not find PlayerData for " + player.getPlayer().getName() + " when adding to game.");
                super.removePlayer(player);
                player.setState(PlayerState.LOBBY);
                return false;
            }

            MicroBattlesTeam assignedTeam = assignTeam(player);
            if (assignedTeam == null) {
                participantPlayerData.remove(player.getPlayer().getUniqueId());
                super.removePlayer(player);
                player.setState(PlayerState.LOBBY);
                return false;
            }

            try {
                scoreboardManager.ensureScoreboard(player.getPlayer(),
                        Component.text("MicroBattles", NamedTextColor.GOLD));
                teleportToGame(player);
                refreshNameColors();
            } catch (RuntimeException exception) {
                assignedTeam.removePlayer(player);
                participantPlayerData.remove(player.getPlayer().getUniqueId());
                super.removePlayer(player);
                player.setState(PlayerState.LOBBY);
                scoreboardManager.removeScoreboard(player.getPlayer());
                MicroBattles.getInstance().getLogger().severe(
                        "Failed to admit " + player.getPlayer().getName() + ": " + exception.getMessage());
                return false;
            }
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

        try {
            if (!participantPlayerData.isEmpty()) {
                this.currentMatchInstance = matchService.startMatch("MicroBattles",
                        new ArrayList<>(participantPlayerData.values()));
                MicroBattles.getInstance().getLogger()
                        .info("MicroBattles match started: " + this.currentMatchInstance.getId());
            }
        } catch (RuntimeException exception) {
            this.currentMatchInstance = null;
            MicroBattles.getInstance().getLogger().severe(
                    "Match persistence is unavailable; gameplay will continue without stats: " + exception.getMessage());
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
    public int getMaxAdmissiblePartySize() {
        return teamSize;
    }

    @Override
    public String getPartyAdmissionProblem(int partySize) {
        String generalProblem = super.getPartyAdmissionProblem(partySize);
        if (generalProblem != null) {
            return generalProblem;
        }
        boolean teamHasRoom = teams.values().stream()
                .anyMatch(team -> team.getPlayerCount() + partySize <= teamSize);
        return teamHasRoom ? null
                : "No MicroBattles team currently has room for all " + partySize + " party members.";
    }

    @Override
    public boolean addPlayerToAvailableTeam(CookiePlayer player) {
        return !isGameEnded() && addPlayer(player);
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
            scoreboardManager.ensureScoreboard(bukkitPlayer, Component.text("MicroBattles", NamedTextColor.GOLD));
            scoreboardManager.updateLine(bukkitPlayer, 6, Component.empty());
            scoreboardManager.updateLine(bukkitPlayer, 5,
                    Component.text("Teams Left: ", NamedTextColor.WHITE)
                            .append(Component.text(getActiveTeamsCount(), NamedTextColor.GREEN)));
            scoreboardManager.updateLine(bukkitPlayer, 4,
                    Component.text("Kills: ", NamedTextColor.WHITE)
                            .append(Component.text(getPlayerKills(player), NamedTextColor.GREEN)));
            scoreboardManager.updateLine(bukkitPlayer, 3, Component.text(" "));
            scoreboardManager.updateLine(bukkitPlayer, 2, Component.text("  "));
            scoreboardManager.updateLine(bukkitPlayer, 1,
                    Component.text("www.cookie-build.com", NamedTextColor.YELLOW));

            int line = 11;
            for (MicroBattlesTeam team : teams.values()) {
                scoreboardManager.updateLine(bukkitPlayer, line--,
                        Component.text(getTeamMarker(team.getName()) + " " + team.getName() + ": ",
                                getTeamTextColor(team.getName()))
                                .append(Component.text(team.getAlivePlayers().size(), NamedTextColor.WHITE)));
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

    /** Records eligible enemy damage so a later elimination can award assists. */
    public void recordDamage(CookiePlayer victim, CookiePlayer attacker) {
        if (getState() != GameState.RUNNING || victim == null || attacker == null || victim.equals(attacker)
                || !getPlayers().contains(victim) || !getPlayers().contains(attacker)
                || arePlayersInSameTeam(victim, attacker)) {
            return;
        }
        recentAttackers.computeIfAbsent(victim.getPlayer().getUniqueId(), ignored -> new HashMap<>())
                .put(attacker.getPlayer().getUniqueId(), System.currentTimeMillis());
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
        if (getState() == GameState.FINISHED) {
            return;
        }
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
                List<MatchService.Performance> performances = participantPlayerData.keySet().stream()
                        .map(playerId -> {
                            boolean winner = winnerPlayerDataList.stream()
                                    .anyMatch(player -> player.getId().equals(playerId));
                            return new MatchService.Performance(playerId,
                                getKillsThisMatch(playerId), getDeathsThisMatch(playerId),
                                playerAssistsThisMatch.getOrDefault(playerId, 0),
                                createPerformanceMetrics(playerId, winner));
                        })
                        .toList();
                matchService.completeMatch(this.currentMatchInstance, winnerPlayerDataList, performances);
            } catch (Exception e) {
                MicroBattles.getInstance().getLogger().severe("Error during match finalization: " + e.getMessage());
            }
        }

        MinigameProgressionService progressionService = new MinigameProgressionService(null);
        String rewardSource = currentMatchInstance == null
                ? "micro-game:" + getGameId() + ":reward"
                : "match:" + currentMatchInstance.getId() + ":micro-reward";
        for (UUID playerId : participantPlayerData.keySet()) {
            boolean isWinner = winnerPlayerDataList.stream().anyMatch(player -> player.getId().equals(playerId));
            int kills = getKillsThisMatch(playerId);
            int assists = playerAssistsThisMatch.getOrDefault(playerId, 0);
            int coinsGained = (isWinner ? 50 : 10) + kills * 5 + assists * 2;
            int xpGained = (isWinner ? 100 : 25) + kills * 10 + assists * 5;
            try {
                int oldLevel = progressionService.getLevel(playerId, MinigameProgressionService.MICROBATTLES);
                var stats = progressionService.applyReward(playerId, MinigameProgressionService.MICROBATTLES,
                        xpGained, coinsGained, rewardSource);
                LobbyScoreboard.invalidatePlayerCache(playerId);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    if (stats.getLevel() > oldLevel) {
                        player.sendMessage("§6§l✦ LEVEL UP! ✦");
                        player.sendMessage("§eYou reached level §a" + stats.getLevel() + "§e in MicroBattles!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                    player.sendMessage(LocaleManager.getMessage(isWinner ? "reward.victory" : "reward.defeat",
                            player.locale(), coinsGained, xpGained));
                }
            } catch (RuntimeException exception) {
                MicroBattles.getInstance().getLogger()
                        .severe("Failed to save rewards for player " + playerId + ": " + exception.getMessage());
            }
        }
        for (UUID playerId : participantPlayerData.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                boolean winner = winnerPlayerDataList.stream().anyMatch(candidate -> candidate.getId().equals(playerId));
                com.cookiebuild.cookiedough.CookieDough.getInstance().getGoalTracker()
                        .recordMatch(player, "MicroBattles", winner, getKillsThisMatch(playerId));
            }
        }
        sendPostMatchSummary(winnerPlayerDataList);
        offerReplay();
        scheduleCleanup(20L * 10);
    }

    @Override
    public synchronized void removePlayer(CookiePlayer player) {
        boolean wasParticipant = getPlayers().contains(player);
        super.removePlayer(player);
        if (!wasParticipant) {
            return;
        }
        // Restore view distance
        if (playerOriginalViewDistances.containsKey(player.getPlayer().getUniqueId())) {
            player.getPlayer().setViewDistance(playerOriginalViewDistances.get(player.getPlayer().getUniqueId()));
            playerOriginalViewDistances.remove(player.getPlayer().getUniqueId());
        }

        scoreboardManager.removeScoreboard(player.getPlayer());
        recentAttackers.remove(player.getPlayer().getUniqueId());
        recentAttackers.values().forEach(attackers -> attackers.remove(player.getPlayer().getUniqueId()));
        refreshNameColors();
        if (getState() == GameState.RUNNING) {
            checkForWinner();
        } else {
            participantPlayerData.remove(player.getPlayer().getUniqueId());
        }
    }

    @Override
    protected void onPlayerRemoved(CookiePlayer player) {
        teams.values().forEach(team -> team.removePlayer(player));
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
        refreshNameColors();
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
        return teamColor != null ? getColorCode(teamColor) + getTeamMarker(teamColor) + " "
                + player.getPlayer().getName()
                : player.getPlayer().getName();
    }

    public void handlePlayerFall(CookiePlayer player) {
        if (player.getState() == com.cookiebuild.cookiedough.player.PlayerState.IN_GAME) {
            handlePlayerDeath(player, null);
        }
    }

    public void handlePlayerDeath(CookiePlayer victim, CookiePlayer killer) {
        if (getState() != GameState.RUNNING || victim == null || victim.getState() != PlayerState.IN_GAME) {
            return;
        }
        UUID victimId = victim.getPlayer().getUniqueId();
        long cutoff = System.currentTimeMillis() - ASSIST_WINDOW_MILLIS;
        Map<UUID, Long> attackers = recentAttackers.remove(victimId);
        if (attackers != null) {
            if (killer == null) {
                UUID latestAttackerId = attackers.entrySet().stream()
                        .filter(entry -> entry.getValue() >= cutoff)
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse(null);
                if (latestAttackerId != null) {
                    killer = getPlayers().stream()
                            .filter(player -> player.getPlayer().getUniqueId().equals(latestAttackerId))
                            .filter(player -> !arePlayersInSameTeam(victim, player))
                            .findFirst().orElse(null);
                }
            }
            UUID killerId = killer == null ? null : killer.getPlayer().getUniqueId();
            attackers.entrySet().stream()
                    .filter(entry -> entry.getValue() >= cutoff && !entry.getKey().equals(killerId))
                    .map(entry -> getPlayers().stream()
                            .filter(player -> player.getPlayer().getUniqueId().equals(entry.getKey()))
                            .findFirst().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .filter(assister -> !arePlayersInSameTeam(victim, assister))
                    .forEach(this::recordAssist);
        }

        MicroBattlesTeam victimTeam = getPlayerTeam(victim);
        victim.setState(PlayerState.SPECTATING);
        playerDeathsThisMatch.put(victim.getPlayer().getUniqueId(),
                getDeathsThisMatch(victim.getPlayer().getUniqueId()) + 1);
        if (killer != null && !arePlayersInSameTeam(victim, killer)) {
            playerKillsThisMatch.put(killer.getPlayer().getUniqueId(),
                    getKillsThisMatch(killer.getPlayer().getUniqueId()) + 1);
            killer.getPlayer().sendMessage("§aYou killed " + getColoredPlayerName(victim));
            boolean teamEliminated = victimTeam != null && victimTeam.getPlayers().stream()
                    .filter(player -> !player.equals(victim))
                    .noneMatch(player -> player.getState() == PlayerState.IN_GAME
                            && player.getPlayer().getGameMode() != GameMode.SPECTATOR);
            if (teamEliminated) {
                playerTeamsEliminatedThisMatch.merge(killer.getPlayer().getUniqueId(), 1, Integer::sum);
            }
        }
        victim.getPlayer().setGameMode(GameMode.SPECTATOR);
        victim.getPlayer().sendMessage(LocaleManager.getMessage("game.player_died", victim.getPlayer().locale()));
        checkForWinner();
    }

    public void respawnPlayerToTeamSpawn(CookiePlayer player) {
        Location spawnLocation = map.getTeamSpawn(getTeamNumber(player));
        spawnLocation.setWorld(map.getWorld());
        if (!spawnLocation.getChunk().isLoaded() && !spawnLocation.getChunk().load(true)) {
            throw new IllegalStateException("Could not load the MicroBattles arena chunk for "
                    + player.getPlayer().getName());
        }
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
        // Constructing a MicroBattlesGame synchronously loads a world and used
        // to freeze the queue for roughly three seconds. Only promote here.
        MicroBattles.activateNextGame();
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
        if (!spawnLocation.getChunk().isLoaded() && !spawnLocation.getChunk().load(true)) {
            throw new IllegalStateException("Could not load the MicroBattles arena chunk for "
                    + player.getPlayer().getName());
        }
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
        if (getState() != GameState.FINISHED) {
            setState(GameState.FINISHED);
        }
        cleanupGameResources();
    }

    private Map<String, Object> createPerformanceMetrics(UUID playerId, boolean winner) {
        Map<String, Object> metrics = new HashMap<>();
        CookiePlayer player = getPlayers().stream()
                .filter(candidate -> candidate.getPlayer().getUniqueId().equals(playerId))
                .findFirst().orElse(null);
        MicroBattlesTeam team = player == null ? null : getPlayerTeam(player);
        metrics.put("team", team == null ? "Unknown" : team.getName());

        String selection = KitManager.getInstance().getSelectedKit(playerId);
        String[] parts = selection == null ? new String[0] : selection.split(":", 2);
        metrics.put("kit", parts.length > 0 && !parts[0].isBlank() ? parts[0] : "Default");
        int tier = 0;
        if (parts.length == 2) {
            try {
                tier = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                tier = 0;
            }
        }
        metrics.put("kitTier", tier);
        metrics.put("teamsEliminated", playerTeamsEliminatedThisMatch.getOrDefault(playerId, 0));
        metrics.put("won", winner);
        metrics.put("weeklyRotation", KitManager.getInstance().getWeeklyFreeKits());
        return metrics;
    }

    private void refreshNameColors() {
        for (CookiePlayer viewer : getPlayers()) {
            Player viewerPlayer = viewer.getPlayer();
            scoreboardManager.ensureScoreboard(viewerPlayer, Component.text("MicroBattles", NamedTextColor.GOLD));
            Scoreboard scoreboard = scoreboardManager.getScoreboard(viewerPlayer);
            if (scoreboard == null) {
                continue;
            }
            for (MicroBattlesTeam microTeam : teams.values()) {
                String scoreboardTeamName = "mb_" + microTeam.getName().toLowerCase();
                Team scoreboardTeam = scoreboard.getTeam(scoreboardTeamName);
                if (scoreboardTeam == null) {
                    scoreboardTeam = scoreboard.registerNewTeam(scoreboardTeamName);
                }
                scoreboardTeam.color(getTeamTextColor(microTeam.getName()));
                scoreboardTeam.prefix(Component.text(getColorCode(microTeam.getName())
                        + getTeamMarker(microTeam.getName()) + " "));
                for (String entry : new ArrayList<>(scoreboardTeam.getEntries())) {
                    scoreboardTeam.removeEntry(entry);
                }
                for (CookiePlayer teammate : microTeam.getPlayers()) {
                    scoreboardTeam.addEntry(teammate.getPlayer().getName());
                }
            }
        }
    }

    private NamedTextColor getTeamTextColor(String teamName) {
        return switch (teamName.toLowerCase()) {
            case "red" -> NamedTextColor.RED;
            case "blue" -> NamedTextColor.BLUE;
            case "yellow" -> NamedTextColor.YELLOW;
            case "green" -> NamedTextColor.GREEN;
            default -> NamedTextColor.WHITE;
        };
    }

    private String getTeamMarker(String teamName) {
        return switch (teamName.toLowerCase()) {
            case "red" -> "●";
            case "blue" -> "◆";
            case "yellow" -> "▲";
            case "green" -> "■";
            default -> "◇";
        };
    }

    private void sendPostMatchSummary(List<PlayerData> winners) {
        for (UUID playerId : participantPlayerData.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            boolean winner = winners.stream().anyMatch(candidate -> candidate.getId().equals(playerId));
            String outcome = winners.isEmpty() ? "DRAW" : winner ? "VICTORY" : "ELIMINATED";
            NamedTextColor outcomeColor = winner ? NamedTextColor.GREEN
                    : winners.isEmpty() ? NamedTextColor.YELLOW : NamedTextColor.RED;
            player.showTitle(Title.title(Component.text(outcome, outcomeColor),
                    Component.text("Your contribution", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            player.sendMessage(Component.text("──────── Match Summary ────────", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Kills " + getKillsThisMatch(playerId), NamedTextColor.GREEN)
                    .append(Component.text("  •  Assists "
                            + playerAssistsThisMatch.getOrDefault(playerId, 0), NamedTextColor.AQUA))
                    .append(Component.text("  •  Teams eliminated "
                            + playerTeamsEliminatedThisMatch.getOrDefault(playerId, 0), NamedTextColor.YELLOW)));
            player.sendMessage(Component.text(
                    com.cookiebuild.cookiedough.CookieDough.getInstance().getGoalTracker().summary(playerId),
                    NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text("[SHARE FEEDBACK]", NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.openUrl("https://www.cookie-build.com"))
                    .hoverEvent(HoverEvent.showText(Component.text(
                            "Share ideas and report kit or map balance issues", NamedTextColor.GRAY))));
        }
    }

    private void scheduleCleanup(long delayTicks) {
        if (cleanupStarted || cleanupTask != null) {
            return;
        }
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupTask = null;
                cleanupGameResources();
            }
        }.runTaskLater(MicroBattles.getInstance(), delayTicks);
    }

    private synchronized void cleanupGameResources() {
        if (cleanupStarted) {
            return;
        }
        cleanupStarted = true;
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        for (CookiePlayer player : new ArrayList<>(getPlayers())) {
            if (player.getPlayer().isOnline()) {
                LobbyManager.teleportPlayerToLobby(player);
            }
            removePlayer(player);
        }
        scoreboardManager.clear();
        cleanupMap();
        GameManager.removeGame(this);
        MicroBattles.requestStandbyRefill();
    }
}
