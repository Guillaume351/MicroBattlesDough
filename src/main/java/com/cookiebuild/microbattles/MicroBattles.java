package com.cookiebuild.microbattles;

import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.game.MicroBattlesMatchPerformance;
import com.cookiebuild.microbattles.listener.InGamePlayerEventListener;
import com.cookiebuild.microbattles.listener.KitEffectListener;
import com.cookiebuild.microbattles.map.MapManager;

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

        // Register MicroBattles entities with Hibernate
        HibernateUtil.registerEntity(MicroBattlesMatchPerformance.class);

        InGamePlayerEventListener inGamePlayerEventListener = new InGamePlayerEventListener();
        MapManager.inGamePlayerEventListener = inGamePlayerEventListener;
        MapManager.loadGameMaps();

        MicroBattles.registerNewGame();

        getServer().getPluginManager().registerEvents(inGamePlayerEventListener, this);
        getServer().getPluginManager().registerEvents(new KitEffectListener(), this);
    }
}