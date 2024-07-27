package com.cookiebuild.microbattles;

import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.map.MapManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MicroBattles extends JavaPlugin {
    private static MicroBattles instance;

    public static MicroBattles getInstance() {
        return instance;
    }

    /**
     * Register a new MicroBattles game
     */
    public static void registerNewGame() {
        GameManager.addGame(new MicroBattlesGame());
    }

    @Override
    public void onEnable() {
        instance = this;
        saveResource("config.yml", false);

        MapManager.loadGameMaps();

        MicroBattles.registerNewGame();
    }
}