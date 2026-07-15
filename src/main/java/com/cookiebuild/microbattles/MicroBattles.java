package com.cookiebuild.microbattles;

import java.time.Duration;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.StandbyGamePool;
import com.cookiebuild.cookiedough.game.StandbyRefillGate;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.microbattles.commands.KitCommand;
import com.cookiebuild.microbattles.commands.MapVoteCommand;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.listener.InGamePlayerEventListener;
import com.cookiebuild.microbattles.listener.KitEffectListener;
import com.cookiebuild.microbattles.listener.KitSelectorListener;
import com.cookiebuild.microbattles.map.MapManager;

public class MicroBattles extends JavaPlugin {
    private static final int STANDBY_GAME_TARGET = 3;
    private static final long STANDBY_REFILL_RETRY_TICKS = 20L * 5L;
    private static MicroBattles instance;
    private final StandbyGamePool<MicroBattlesGame> standbyGames =
            new StandbyGamePool<>(STANDBY_GAME_TARGET);
    private final StandbyRefillGate standbyRefillGate =
            new StandbyRefillGate(Duration.ofSeconds(30));
    private boolean standbyRefillScheduled;
    private boolean shuttingDown;

    public static MicroBattles getInstance() {
        return instance;
    }

    public static boolean registerNewGame() {
        try {
            MicroBattlesGame game = new MicroBattlesGame();
            GameManager.addGame(game);
            instance.getLogger().info("Registered MicroBattles game " + game.getGameId());
            return true;
        } catch (RuntimeException error) {
            instance.getLogger().warning("MicroBattles unavailable until a valid arena can be loaded: "
                    + error.getMessage());
            return false;
        }
    }

    /** Promotes an already loaded world without doing I/O on the queue tick. */
    public static void activateNextGame() {
        if (instance == null || instance.shuttingDown) {
            return;
        }
        MicroBattlesGame game = instance.standbyGames.poll();
        if (game == null) {
            instance.getLogger().warning("No preloaded MicroBattles standby is available; "
                    + "the next arena will be prepared once gameplay is idle");
            requestStandbyRefill();
            return;
        }
        GameManager.addGame(game);
        instance.getLogger().info("Activated preloaded MicroBattles game " + game.getGameId()
                + " (standby remaining=" + instance.standbyGames.size() + ")");
        requestStandbyRefill();
    }

    public static void requestStandbyRefill() {
        if (instance == null || instance.shuttingDown || instance.standbyRefillScheduled
                || !instance.standbyGames.needsRefill()) {
            return;
        }
        instance.standbyRefillScheduled = true;
        instance.getServer().getScheduler().runTaskLater(instance, () -> {
            if (instance == null || instance.shuttingDown) {
                return;
            }
            instance.standbyRefillScheduled = false;
            if (!instance.isSafeToRefill()) {
                requestStandbyRefill();
                return;
            }
            instance.preloadStandbyGames();
            instance.activatePreparedGameIfMissing();
            if (instance.standbyGames.needsRefill()) {
                requestStandbyRefill();
            }
        }, STANDBY_REFILL_RETRY_TICKS);
    }

    private boolean isSafeToRefill() {
        // WorldCreator is synchronous and may take several seconds even when no
        // match is running. Do not impose that pause on lobby users either, and
        // require a sustained empty interval so reconnects cannot race a refill.
        boolean activeGameplay = GameManager.getGames().stream().anyMatch(game -> !game.getPlayers().isEmpty()
                || game.getState() == GameState.STARTING
                || game.getState() == GameState.RUNNING);
        return standbyRefillGate.canRefill(!Bukkit.getOnlinePlayers().isEmpty(), activeGameplay);
    }

    private void preloadStandbyGames() {
        while (!shuttingDown && standbyGames.needsRefill()) {
            long startedAt = System.nanoTime();
            try {
                MicroBattlesGame game = new MicroBattlesGame();
                if (!standbyGames.offer(game)) {
                    game.shutdown();
                    break;
                }
                getLogger().info("Preloaded MicroBattles standby " + game.getGameId()
                        + " (" + standbyGames.size() + "/" + standbyGames.targetSize()
                        + ", load_ms=" + elapsedMillis(startedAt) + ")");
            } catch (RuntimeException error) {
                getLogger().warning("Could not preload MicroBattles standby: " + error.getMessage());
                break;
            }
        }
    }

    private void activatePreparedGameIfMissing() {
        boolean hasOpenGame = GameManager.getGames().stream()
                .filter(MicroBattlesGame.class::isInstance)
                .anyMatch(game -> game.getState() == GameState.OPEN);
        if (!hasOpenGame) {
            activateNextGame();
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Load Maps
        InGamePlayerEventListener inGamePlayerEventListener = new InGamePlayerEventListener();
        MapManager.inGamePlayerEventListener = inGamePlayerEventListener;
        MapManager.loadGameMaps();

        // Register a new game instance
        if (!MicroBattles.registerNewGame()) {
            getLogger().warning("No game registered; NPC and Quick Play stay fail-closed.");
        } else {
            // Finish the expensive world preparation before Paper reports the
            // server ready. Countdown ticks only promote these prepared games.
            preloadStandbyGames();
        }

        // Initialize services and managers
        MinigameProgressionService minigameStatsService = CookieDough.createMinigameProgressionService();
        PlayerStatsService playerStatsService = CookieDough.getPlayerStatsService();
        KitManager kitManager = KitManager.getInstance();

        // Setup Listeners
        KitSelectorListener kitSelectorListener = new KitSelectorListener(kitManager, minigameStatsService,
                playerStatsService);

        // Register Events
        getServer().getPluginManager().registerEvents(inGamePlayerEventListener, this);
        getServer().getPluginManager().registerEvents(new KitEffectListener(), this);
        getServer().getPluginManager().registerEvents(kitSelectorListener, this);
        getServer().getPluginManager().registerEvents(kitSelectorListener.getKitSelectionUI(), this);

        // Register Commands
        getCommand("kit").setExecutor(new KitCommand(kitSelectorListener));
        MapVoteCommand mapVoteCommand = new MapVoteCommand();
        getCommand("mbvote").setExecutor(mapVoteCommand);
        getCommand("mbvote").setTabCompleter(mapVoteCommand);
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        standbyGames.drain();
        GameManager.getGames().stream()
                .filter(MicroBattlesGame.class::isInstance)
                .map(MicroBattlesGame.class::cast)
                .toList()
                .forEach(MicroBattlesGame::shutdown);
        MapManager.cleanupLoadedMaps();
        instance = null;
    }
}
