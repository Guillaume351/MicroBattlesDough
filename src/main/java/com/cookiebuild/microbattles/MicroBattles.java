package com.cookiebuild.microbattles;

import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.microbattles.commands.KitCommand;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.listener.InGamePlayerEventListener;
import com.cookiebuild.microbattles.listener.KitEffectListener;
import com.cookiebuild.microbattles.listener.KitSelectorListener;
import com.cookiebuild.microbattles.map.MapManager;

public class MicroBattles extends JavaPlugin {
    private static MicroBattles instance;

    public static MicroBattles getInstance() {
        return instance;
    }

    public static void registerNewGame() {
        GameManager.addGame(new MicroBattlesGame());
    }

    @Override
    public void onEnable() {
        instance = this;
        saveResource("config.yml", false);

        // Load Maps
        InGamePlayerEventListener inGamePlayerEventListener = new InGamePlayerEventListener();
        MapManager.inGamePlayerEventListener = inGamePlayerEventListener;
        MapManager.loadGameMaps();

        // Register a new game instance
        MicroBattles.registerNewGame();

        // Initialize services and managers
        MinigameStatsService minigameStatsService = CookieDough.createMinigameStatsService();
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
    }
}