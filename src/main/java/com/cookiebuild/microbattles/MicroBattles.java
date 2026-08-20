package com.cookiebuild.microbattles;

import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.BukkitArenaPreparationScheduler;
import com.cookiebuild.cookiedough.game.ArenaPreparationPipeline;
import com.cookiebuild.cookiedough.game.StandbyArenaService;
import com.cookiebuild.cookiedough.game.StandbyRefillPolicy;
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
    private static MicroBattles instance;
    private StandbyArenaService<MapManager.PreparedMap, MicroBattlesGame> arenas;
    private boolean shuttingDown;
    private NamespacedKey kitSelectorKey;

    public static MicroBattles getInstance() {
        return instance;
    }

    public NamespacedKey getKitSelectorKey() {
        return kitSelectorKey;
    }

    public static boolean registerNewGame() {
        return instance != null && !instance.shuttingDown && instance.arenas.request(0L);
    }

    /** Promotes an already loaded world without doing I/O on the queue tick. */
    public static void activateNextGame() {
        if (instance == null || instance.shuttingDown) {
            return;
        }
        instance.arenas.activateNext();
    }

    public static void requestStandbyRefill() {
        if (instance != null && !instance.shuttingDown) {
            instance.arenas.request(StandbyRefillPolicy.RUNTIME_DELAY_TICKS);
        }
    }

    private MapManager.PreparedMap planArena() {
        try {
            String mapName = MapManager.getRandomMapName();
            return MapManager.plan(UUID.randomUUID(), mapName);
        } catch (java.io.IOException error) {
            throw new CompletionException(error);
        }
    }

    private static MapManager.PreparedMap prepareArenaIo(MapManager.PreparedMap plan) {
        try {
            return MapManager.prepareIo(plan);
        } catch (java.io.IOException error) {
            throw new CompletionException(error);
        }
    }

    private static ArenaPreparationPipeline.WorldLoad<MicroBattlesGame> loadArena(
            MapManager.PreparedMap prepared) {
        return MapManager.loadPreparedAsync(prepared).map(map -> {
            try {
                return new MicroBattlesGame(prepared.gameId(), map, prepared.mapName());
            } catch (RuntimeException error) {
                if (!MapManager.discardLoadedWorld(prepared)) {
                    MicroBattles.getInstance().getLogger().warning(
                            "Could not unload partially constructed MicroBattles arena " + prepared.gameId());
                }
                throw error;
            }
        });
    }

    @Override
    public void onEnable() {
        instance = this;
        kitSelectorKey = new NamespacedKey(this, "kit_selector");
        saveDefaultConfig();

        // Load Maps
        InGamePlayerEventListener inGamePlayerEventListener = new InGamePlayerEventListener();
        MapManager.inGamePlayerEventListener = inGamePlayerEventListener;
        MapManager.loadGameMaps();
        arenas = StandbyArenaService.asynchronous(
                "MicroBattles", new BukkitArenaPreparationScheduler(this), this::planArena,
                MicroBattles::prepareArenaIo, MicroBattles::loadArena, MapManager::discardPrepared,
                () -> GameManager.getGames().stream().filter(MicroBattlesGame.class::isInstance)
                        .anyMatch(game -> game.getState() == GameState.OPEN),
                GameManager::addGame, MicroBattlesGame::shutdown, getLogger(), true);

        // Register a new game instance
        if (!MicroBattles.registerNewGame()) {
            getLogger().warning("No game registered; NPC and Quick Play stay fail-closed.");
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
        if (arenas != null) arenas.shutdown();
        GameManager.getGames().stream()
                .filter(MicroBattlesGame.class::isInstance)
                .map(MicroBattlesGame.class::cast)
                .toList()
                .forEach(MicroBattlesGame::shutdown);
        MapManager.cleanupLoadedMaps();
        instance = null;
    }
}
