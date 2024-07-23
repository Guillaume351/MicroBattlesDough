package com.cookiebuild.microbattles;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameStatus;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class MicroBattles extends JavaPlugin {
    private final List<MicroBattlesGame> activeGames = new ArrayList<>();
    private static MicroBattles instance;

    public static MicroBattles getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        // Example of initializing and registering a game
        MicroBattlesGame game1 = new MicroBattlesGame(1);
        activeGames.add(game1);
        registerGames();
    }

    private void registerGames() {
        for (GameStatus game : activeGames) {
            CookieDough.getLobbyManager().registerGame(game);
        }
    }
}