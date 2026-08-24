package com.cookiebuild.microbattles.game;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.PlayerActivitySnapshot;
import com.cookiebuild.cookiedough.game.ReconnectableGame;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.lobby.LobbyScoreboard;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.service.MatchService;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.kits.KitDisplayNames;
import com.cookiebuild.microbattles.listener.KitSelectorListener;
import com.cookiebuild.microbattles.map.GameMap;
import com.cookiebuild.microbattles.map.MapManager;
import com.cookiebuild.microbattles.ui.MicroBattlesScoreboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;

public class MicroBattlesGame extends Game implements ReconnectableGame {
    private static final long RECONNECT_GRACE_MILLIS = Duration.ofSeconds(60).toMillis();
    int teamSize = 3;
    private GameMap map;
    private final Map<String, MicroBattlesTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, Integer> playerOriginalViewDistances = new HashMap<>();
    private final Map<UUID, Long> disconnectedAt = new HashMap<>();
    private final Map<UUID, PlayerActivitySnapshot> reconnectSnapshots = new HashMap<>();
    private final Map<UUID, String> reconnectTeams = new HashMap<>();
    private final Map<UUID, String> reconnectKits = new HashMap<>();

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
    private CompletableFuture<Match> matchFuture = CompletableFuture.completedFuture(null);
    private final Set<UUID> participantIds = new LinkedHashSet<>();
    private final HashMap<UUID, Integer> playerKillsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerDeathsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerAssistsThisMatch = new HashMap<>();
    private final HashMap<UUID, Integer> playerTeamsEliminatedThisMatch = new HashMap<>();
    private static final int MAXIMUM_RUNNING_SECONDS = 300;
    private int runningSeconds;
    private boolean timeoutWarningSent;
    private boolean timedOut;
    private boolean outcomePersisted;

    public MicroBattlesGame(UUID gameId, GameMap preparedMap, String mapName) {
        super("MicroBattles", gameId);
        setupTeams();

        setCapacity(teamSize * 4);
        this.scoreboardManager = new MicroBattlesScoreboardManager();
        this.matchService = new MatchService(null);

        map = java.util.Objects.requireNonNull(preparedMap, "preparedMap");
        this.wallCoordinates = MapManager.getWallCoordinatesForMap(mapName);
        map.identifyWallBlocks(wallCoordinates);
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
                player.getPlayer().sendMessage("§c" + LocaleManager.getMessage(
                        "microbattles.party.too_large", player.getPlayer().locale()));
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
            UUID playerId = player.getPlayer().getUniqueId();
            participantIds.add(playerId);
            KitManager.getInstance().preload(playerId);

            MicroBattlesTeam assignedTeam = assignTeam(player);
            if (assignedTeam == null) {
                participantIds.remove(player.getPlayer().getUniqueId());
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
                participantIds.remove(player.getPlayer().getUniqueId());
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
        runningSeconds = 0;
        timeoutWarningSent = false;
        timedOut = false;
        Set<UUID> starters = Set.copyOf(participantIds);
        matchFuture = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(MicroBattles.getInstance(), () -> {
            try {
                Match match = matchService.startMatchByPlayerIds("MicroBattles", starters);
                matchFuture.complete(match);
                MicroBattles.getInstance().getLogger().info("MicroBattles match started: " + match.getId());
            } catch (RuntimeException exception) {
                MicroBattles.getInstance().getLogger().severe(
                        "Match persistence is unavailable; gameplay will continue without stats: "
                                + exception.getMessage());
                matchFuture.complete(null);
            }
        });

        equipKitsForGameStart();
    }

    @Override
    public boolean supportsSpectating() {
        return true;
    }

    @Override
    protected Location spectatorDestination(CookiePlayer cookiePlayer) {
        return map == null || map.getWorld() == null
                ? null : map.getTeamSpawn(0).clone().add(0.0, 12.0, 0.0);
    }

    private void equipKitsForGameStart() {
        KitManager kitManager = KitManager.getInstance();
        for (CookiePlayer cookiePlayer : getPlayers()) {
            kitManager.equipSelectedKit(cookiePlayer.getPlayer());
            giveTeamColoredWool(cookiePlayer);

            String selectedKitName = kitManager.getSelectedKit(cookiePlayer.getPlayer().getUniqueId());
            String kitName = "Default";
            if (selectedKitName != null && !selectedKitName.isEmpty()) {
                kitName = selectedKitName.split(":")[0];
            }

            String localizedKitName = KitDisplayNames.localized(
                    kitName, cookiePlayer.getPlayer().locale());
            String originalName = cookiePlayer.getPlayer().getName();
            String kitDisplayName = "§f" + originalName + "\n§7[" + localizedKitName + "]";
            cookiePlayer.getPlayer().setDisplayName(kitDisplayName);
            cookiePlayer.getPlayer().setPlayerListName(kitDisplayName);

            cookiePlayer.getPlayer().sendMessage(
                    "§a" + LocaleManager.getMessage(
                            "kit.equipped", cookiePlayer.getPlayer().locale(), localizedKitName));
            cookiePlayer.getPlayer().showTitle(
                    Title.title(
                            Component.text("§6" + localizedKitName),
                            Component.text("§a" + LocaleManager.getMessage(
                                    "microbattles.kit.equipped.subtitle", cookiePlayer.getPlayer().locale())),
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
            expireReconnectReservations();
            runningSeconds++;
            if (!timeoutWarningSent && runningSeconds >= MAXIMUM_RUNNING_SECONDS - 60) {
                timeoutWarningSent = true;
                for (CookiePlayer player : getPlayers()) {
                    player.getPlayer().sendMessage(Component.text(LocaleManager.getMessage(
                            "microbattles.timeout.warning", player.getPlayer().locale()), NamedTextColor.YELLOW));
                }
            }
            if (runningSeconds >= MAXIMUM_RUNNING_SECONDS) {
                timedOut = true;
                endGame(timeoutWinner());
                return;
            }
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
                } else {
                    countdownInfo = LocaleManager.getMessage("microbattles.time_left", bukkitPlayer.locale(),
                            formatSeconds(MAXIMUM_RUNNING_SECONDS - runningSeconds));
                }
            } else {
                gameStateText = LocaleManager.getMessage("game.ended", bukkitPlayer.locale());
            }

            bukkitPlayer.sendActionBar(net.kyori.adventure.text.Component.text(gameStateText + " " + countdownInfo));
            scoreboardManager.ensureScoreboard(bukkitPlayer, Component.text("MicroBattles", NamedTextColor.GOLD));
            scoreboardManager.updateLine(bukkitPlayer, 6, Component.empty());
            scoreboardManager.updateLine(bukkitPlayer, 5,
                    Component.text(LocaleManager.getMessage("microbattles.scoreboard.teams_left",
                            bukkitPlayer.locale()), NamedTextColor.WHITE)
                            .append(Component.text(getActiveTeamsCount(), NamedTextColor.GREEN)));
            scoreboardManager.updateLine(bukkitPlayer, 4,
                    Component.text(LocaleManager.getMessage("microbattles.scoreboard.kills",
                            bukkitPlayer.locale()), NamedTextColor.WHITE)
                            .append(Component.text(getPlayerKills(player), NamedTextColor.GREEN)));
            scoreboardManager.updateLine(bukkitPlayer, 3,
                    Component.text(LocaleManager.getMessage("microbattles.scoreboard.time", bukkitPlayer.locale(),
                            formatSeconds(MAXIMUM_RUNNING_SECONDS - runningSeconds)), NamedTextColor.YELLOW));
            scoreboardManager.updateLine(bukkitPlayer, 2, Component.text("  "));
            scoreboardManager.updateLine(bukkitPlayer, 1,
                    Component.text("www.cookie-build.com", NamedTextColor.YELLOW));

            int line = 11;
            for (MicroBattlesTeam team : teams.values()) {
                scoreboardManager.updateLine(bukkitPlayer, line--,
                        Component.text(getTeamMarker(team.getName()) + " " + localizedTeamName(bukkitPlayer, team) + ": ",
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

    private static String formatSeconds(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format(java.util.Locale.ROOT, "%d:%02d", safe / 60, safe % 60);
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

    private MicroBattlesTeam timeoutWinner() {
        List<MicroBattlesTimeoutPolicy.TeamStanding> standings = teams.values().stream().map(team -> {
            List<CookiePlayer> members = team.getPlayers();
            int alive = (int) members.stream()
                    .filter(player -> player.getPlayer().getGameMode() != GameMode.SPECTATOR).count();
            int kills = members.stream().mapToInt(player -> getKillsThisMatch(
                    player.getPlayer().getUniqueId())).sum();
            int teamsEliminated = members.stream().mapToInt(player -> playerTeamsEliminatedThisMatch
                    .getOrDefault(player.getPlayer().getUniqueId(), 0)).sum();
            double health = members.stream()
                    .filter(player -> player.getPlayer().getGameMode() != GameMode.SPECTATOR)
                    .map(CookiePlayer::getPlayer).filter(Player::isOnline)
                    .mapToDouble(Player::getHealth).sum();
            return new MicroBattlesTimeoutPolicy.TeamStanding(
                    team.getName(), alive, teamsEliminated, kills, health);
        }).filter(standing -> standing.alivePlayers() > 0).toList();
        String winner = MicroBattlesTimeoutPolicy.winner(standings).orElse(null);
        return winner == null ? null : teams.get(winner);
    }

    private void endGame(MicroBattlesTeam winningTeam) {
        if (getState() == GameState.FINISHED) {
            return;
        }
        setState(GameState.FINISHED);

        Set<UUID> winnerIds = new LinkedHashSet<>();
        if (winningTeam != null) {
            for (CookiePlayer winner : winningTeam.getPlayers()) {
                winnerIds.add(winner.getPlayer().getUniqueId());
            }
        }
        persistOutcomeAndRewards(winnerIds);
        sendPostMatchSummary(winnerIds);
        if (timedOut) {
            for (CookiePlayer player : getPlayers()) {
                player.getPlayer().sendMessage(Component.text(LocaleManager.getMessage(
                        "microbattles.timeout.result", player.getPlayer().locale()), NamedTextColor.YELLOW));
            }
        }
        offerReplay();
        scheduleCleanup(20L * 10);
    }

    private void persistOutcomeAndRewards(Set<UUID> winnerIds) {
        if (outcomePersisted) return;
        outcomePersisted = true;
        Set<UUID> players = Set.copyOf(participantIds);
        List<MatchService.Performance> performances = players.stream().map(playerId ->
                new MatchService.Performance(playerId, getKillsThisMatch(playerId), getDeathsThisMatch(playerId),
                        playerAssistsThisMatch.getOrDefault(playerId, 0),
                        createPerformanceMetrics(playerId, winnerIds.contains(playerId)))).toList();
        Map<UUID, RewardPlan> rewards = players.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                playerId -> playerId,
                playerId -> {
                    boolean winner = winnerIds.contains(playerId);
                    int kills = getKillsThisMatch(playerId);
                    int assists = playerAssistsThisMatch.getOrDefault(playerId, 0);
                    return new RewardPlan(winner, kills, assists,
                            (winner ? 50 : 10) + kills * 5 + assists * 2,
                            (winner ? 100 : 25) + kills * 10 + assists * 5);
                }));
        CompletableFuture<Match> pendingMatch = matchFuture;
        Bukkit.getScheduler().runTaskAsynchronously(MicroBattles.getInstance(), () -> {
            Match durableMatch = null;
            try {
                durableMatch = pendingMatch.get(5, TimeUnit.SECONDS);
                if (durableMatch != null) {
                    matchService.completeMatchByWinnerIds(durableMatch, winnerIds, performances);
                }
            } catch (Exception exception) {
                MicroBattles.getInstance().getLogger().severe(
                        "Could not persist MicroBattles result: " + exception.getMessage());
            }
            String source = "game:" + getGameId() + ":microbattles-reward";
            MinigameProgressionService progressionService = new MinigameProgressionService(null);
            List<RewardNotice> notices = new ArrayList<>();
            for (Map.Entry<UUID, RewardPlan> entry : rewards.entrySet()) {
                UUID playerId = entry.getKey();
                RewardPlan reward = entry.getValue();
                try {
                    int oldLevel = progressionService.getLevel(playerId, MinigameProgressionService.MICROBATTLES);
                    var result = progressionService.applyReward(playerId, MinigameProgressionService.MICROBATTLES,
                            reward.xp(), reward.coins(), source);
                    com.cookiebuild.cookiedough.CookieDough.getInstance().getGoalTracker()
                            .recordMatch(playerId, "MicroBattles", reward.winner(), reward.kills());
                    notices.add(new RewardNotice(playerId, reward.winner(), reward.coins(), reward.xp(),
                            result.getLevel(), result.getLevel() > oldLevel));
                } catch (RuntimeException exception) {
                    MicroBattles.getInstance().getLogger().severe(
                            "Failed to save rewards for player " + playerId + ": " + exception.getMessage());
                }
            }
            Bukkit.getScheduler().runTask(MicroBattles.getInstance(), () -> notices.forEach(this::sendRewardNotice));
        });
    }

    private void sendRewardNotice(RewardNotice notice) {
        LobbyScoreboard.invalidatePlayerCache(notice.playerId());
        Player player = Bukkit.getPlayer(notice.playerId());
        if (player == null || !player.isOnline()) return;
        if (notice.levelUp()) {
            player.sendMessage(LocaleManager.getMessage("microbattles.level_up.title", player.locale()));
            player.sendMessage(LocaleManager.getMessage("microbattles.level_up.detail", player.locale(), notice.level()));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        player.sendMessage(LocaleManager.getMessage(notice.winner() ? "reward.victory" : "reward.defeat",
                player.locale(), notice.coins(), notice.xp()));
    }

    private record RewardPlan(boolean winner, int kills, int assists, int coins, int xp) {
    }

    private record RewardNotice(UUID playerId, boolean winner, int coins, int xp, int level, boolean levelUp) {
    }

    @Override
    public synchronized void removePlayer(CookiePlayer player) {
        removePlayer(player, "left_game");
    }

    @Override
    public synchronized void removePlayer(CookiePlayer player, String reason) {
        if (player == null || player.getPlayer() == null) return;
        UUID playerId = player.getPlayer().getUniqueId();
        if (getSpectators().stream().anyMatch(viewer ->
                viewer.getPlayer().getUniqueId().equals(playerId))) {
            super.removePlayer(player, reason);
            scoreboardManager.removeScoreboard(player.getPlayer());
            return;
        }
        boolean wasParticipant = getPlayers().contains(player);
        if (wasParticipant && getState() == GameState.RUNNING && "disconnect".equalsIgnoreCase(reason)
                && player.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            if (!disconnectedAt.containsKey(playerId)) {
                disconnectedAt.put(playerId, System.currentTimeMillis());
                reconnectSnapshots.put(playerId, PlayerActivitySnapshot.capture(player.getPlayer()));
                MicroBattlesTeam team = getPlayerTeam(player);
                if (team != null) reconnectTeams.put(playerId, team.getName());
                String selectedKit = KitManager.getInstance().getSelectedKit(playerId);
                if (selectedKit != null) reconnectKits.put(playerId, selectedKit);
            }
            scoreboardManager.removeScoreboard(player.getPlayer());
            return;
        }
        super.removePlayer(player, reason);
        if (!wasParticipant) {
            return;
        }
        // Restore view distance
        if (playerOriginalViewDistances.containsKey(playerId)) {
            player.getPlayer().setViewDistance(playerOriginalViewDistances.get(playerId));
            playerOriginalViewDistances.remove(playerId);
        }

        disconnectedAt.remove(playerId);
        reconnectSnapshots.remove(playerId);
        reconnectTeams.remove(playerId);
        reconnectKits.remove(playerId);
        MicroBattles plugin = MicroBattles.getInstance();
        if (plugin != null) plugin.clearKitEffectState(playerId);

        scoreboardManager.removeScoreboard(player.getPlayer());
        recentAttackers.remove(player.getPlayer().getUniqueId());
        recentAttackers.values().forEach(attackers -> attackers.remove(player.getPlayer().getUniqueId()));
        refreshNameColors();
        if (getState() == GameState.RUNNING) {
            checkForWinner();
        } else {
            participantIds.remove(player.getPlayer().getUniqueId());
        }
    }

    @Override
    public boolean hasReconnectReservation(UUID playerId) {
        Long disconnected = disconnectedAt.get(playerId);
        return getState() == GameState.RUNNING && disconnected != null && reconnectTeams.containsKey(playerId)
                && System.currentTimeMillis() - disconnected <= RECONNECT_GRACE_MILLIS;
    }

    @Override
    public synchronized boolean reconnect(CookiePlayer cookiePlayer) {
        UUID playerId = cookiePlayer.getPlayer().getUniqueId();
        String teamName = reconnectTeams.get(playerId);
        MicroBattlesTeam team = teamName == null ? null : teams.get(teamName);
        CookiePlayer previous = team == null ? null : team.getPlayers().stream()
                .filter(player -> player.getPlayer().getUniqueId().equals(playerId)).findFirst().orElse(null);
        PlayerActivitySnapshot snapshot = reconnectSnapshots.get(playerId);
        String selectedKit = reconnectKits.get(playerId);
        if (team == null || previous == null || snapshot == null || !hasReconnectReservation(playerId)
                || !snapshot.relocate(cookiePlayer.getPlayer(), map.getTeamSpawn(
                        teams.values().stream().toList().indexOf(team)))
                || !restorePlayerAfterReconnect(cookiePlayer)) {
            return false;
        }
        snapshot.applyState(cookiePlayer.getPlayer());
        if (selectedKit != null) KitManager.getInstance().restoreSelectedKit(playerId, selectedKit);
        team.removePlayer(previous);
        if (!team.addPlayer(cookiePlayer)) return false;
        disconnectedAt.remove(playerId);
        reconnectSnapshots.remove(playerId);
        reconnectTeams.remove(playerId);
        reconnectKits.remove(playerId);
        cookiePlayer.setState(PlayerState.IN_GAME);
        scoreboardManager.ensureScoreboard(cookiePlayer.getPlayer(), Component.text("MicroBattles", NamedTextColor.GOLD));
        refreshNameColors();
        return true;
    }

    private void expireReconnectReservations() {
        long now = System.currentTimeMillis();
        for (UUID playerId : List.copyOf(disconnectedAt.keySet())) {
            Long disconnected = disconnectedAt.get(playerId);
            if (disconnected == null || now - disconnected <= RECONNECT_GRACE_MILLIS) continue;
            CookiePlayer previous = getPlayers().stream()
                    .filter(player -> player.getPlayer().getUniqueId().equals(playerId)).findFirst().orElse(null);
            if (previous != null) removePlayer(previous, "reconnect_expired");
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
            killer.getPlayer().sendMessage("§a" + LocaleManager.getMessage(
                    "microbattles.kill", killer.getPlayer().locale(), getColoredPlayerName(victim)));
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
        teleportPlayerSafely(player.getPlayer(), spawnLocation);
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
        teleportPlayerSafely(player.getPlayer(), spawnLocation);

        if (this.getState() != GameState.RUNNING) {
            player.getPlayer().setGameMode(GameMode.SURVIVAL);
            KitSelectorListener.giveKitSelectorCookie(player.getPlayer());
            player.getPlayer().showTitle(
                    Title.title(
                            Component.text("§6" + LocaleManager.getMessage(
                                    "microbattles.kit.menu.title", player.getPlayer().locale())),
                            Component.text("§a" + LocaleManager.getMessage(
                                    "microbattles.kit.selector.hint", player.getPlayer().locale())),
                            Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            updatePlayerNameColor(player);
        }
    }

    @Override
    public void resetGame() {
        super.resetGame();
        // Game invokes resetGame from its constructor before subclass fields are
        // initialized, hence the guarded cleanup.
        if (disconnectedAt != null) disconnectedAt.clear();
        if (reconnectSnapshots != null) reconnectSnapshots.clear();
        if (reconnectTeams != null) reconnectTeams.clear();
        if (reconnectKits != null) reconnectKits.clear();
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
        if (getState() == GameState.RUNNING && !outcomePersisted) {
            persistInterruptedOutcome();
        }
        if (getState() != GameState.FINISHED) {
            setState(GameState.FINISHED);
        }
        cleanupGameResources();
    }

    private void persistInterruptedOutcome() {
        outcomePersisted = true;
        List<MatchService.Performance> performances = Set.copyOf(participantIds).stream()
                .map(playerId -> new MatchService.Performance(
                        playerId,
                        getKillsThisMatch(playerId),
                        getDeathsThisMatch(playerId),
                        playerAssistsThisMatch.getOrDefault(playerId, 0),
                        interruptedPerformanceMetrics(playerId)))
                .toList();
        CompletableFuture<Match> pendingMatch = matchFuture;
        boolean flushed = BoundedAsyncFlush.runAndAwait(() -> {
            try {
                Match durableMatch = pendingMatch.get(1500, TimeUnit.MILLISECONDS);
                if (durableMatch != null) {
                    new MatchService(null).completeMatchByWinnerIds(durableMatch, Set.of(), performances);
                }
            } catch (Exception exception) {
                logWarning("Could not persist interrupted MicroBattles match: " + exception.getMessage());
            }
        }, Duration.ofSeconds(2));
        if (!flushed) {
            logWarning("Interrupted MicroBattles persistence exceeded the 2 second shutdown budget");
        }
    }

    private Map<String, Object> interruptedPerformanceMetrics(UUID playerId) {
        Map<String, Object> metrics = createPerformanceMetrics(playerId, false);
        metrics.put("interrupted", true);
        return metrics;
    }

    private void logWarning(String message) {
        MicroBattles plugin = MicroBattles.getInstance();
        if (plugin != null) plugin.getLogger().warning(message);
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
        metrics.put("durationSeconds", runningSeconds);
        metrics.put("timeout", timedOut);
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

    private String localizedTeamName(Player player, MicroBattlesTeam team) {
        return LocaleManager.getMessage("microbattles.team."
                + team.getName().toLowerCase(java.util.Locale.ROOT), player.locale());
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

    private void sendPostMatchSummary(Set<UUID> winners) {
        for (UUID playerId : participantIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            boolean winner = winners.contains(playerId);
            String outcomeKey = winners.isEmpty() ? "microbattles.outcome.draw"
                    : winner ? "microbattles.outcome.victory" : "microbattles.outcome.eliminated";
            NamedTextColor outcomeColor = winner ? NamedTextColor.GREEN
                    : winners.isEmpty() ? NamedTextColor.YELLOW : NamedTextColor.RED;
            player.showTitle(Title.title(Component.text(LocaleManager.getMessage(outcomeKey, player.locale()), outcomeColor),
                    Component.text(LocaleManager.getMessage("microbattles.summary.contribution", player.locale()),
                            NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            player.sendMessage(Component.text(LocaleManager.getMessage(
                    "microbattles.summary.header", player.locale()), NamedTextColor.GOLD));
            player.sendMessage(Component.text(LocaleManager.getMessage("microbattles.summary.stats", player.locale(),
                    getKillsThisMatch(playerId), playerAssistsThisMatch.getOrDefault(playerId, 0),
                    playerTeamsEliminatedThisMatch.getOrDefault(playerId, 0)), NamedTextColor.GREEN));
            player.sendMessage(Component.text(
                    com.cookiebuild.cookiedough.CookieDough.getInstance().getGoalTracker().summary(playerId),
                    NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text(LocaleManager.getMessage(
                    "microbattles.feedback.action", player.locale()), NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.openUrl("https://www.cookie-build.com"))
                    .hoverEvent(HoverEvent.showText(Component.text(
                            LocaleManager.getMessage("microbattles.feedback.hover", player.locale()),
                            NamedTextColor.GRAY))));
        }
    }

    private void scheduleCleanup(long delayTicks) {
        if (cleanupStarted || cleanupTask != null) {
            return;
        }
        MicroBattles plugin = MicroBattles.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        try {
            cleanupTask = new BukkitRunnable() {
                @Override
                public void run() {
                    cleanupTask = null;
                    cleanupGameResources();
                }
            }.runTaskLater(plugin, delayTicks);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not schedule MicroBattles cleanup retry: " + error.getMessage());
        }
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
        if (!ejectOwnedPlayersToLobby()) {
            cleanupStarted = false;
            MicroBattles.getInstance().getLogger().warning(
                    "Deferring MicroBattles map cleanup until every player reaches the lobby: " + getGameId());
            scheduleCleanup(20L);
            return;
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
