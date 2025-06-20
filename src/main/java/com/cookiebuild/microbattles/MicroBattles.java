package com.cookiebuild.microbattles;

import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.service.PlayerMinigameProgressionService;
import com.cookiebuild.microbattles.commands.KitCommand;
import com.cookiebuild.microbattles.game.MicroBattlesGame;
import com.cookiebuild.microbattles.kits.KitManager;
import com.cookiebuild.microbattles.listener.InGamePlayerEventListener;
import com.cookiebuild.microbattles.listener.KitEffectListener;
import com.cookiebuild.microbattles.listener.KitSelectorListener;
import com.cookiebuild.microbattles.map.MapManager;
import com.cookiebuild.microbattles.ui.ImprovedKitSelectionUI;

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

        InGamePlayerEventListener inGamePlayerEventListener = new InGamePlayerEventListener();
        MapManager.inGamePlayerEventListener = inGamePlayerEventListener;
        MapManager.loadGameMaps();

        MicroBattles.registerNewGame();

        getServer().getPluginManager().registerEvents(inGamePlayerEventListener, this);
        getServer().getPluginManager().registerEvents(new KitEffectListener(), this);

        // Créer le service de progression des mini-jeux
        PlayerMinigameProgressionService progressionService = new PlayerMinigameProgressionService(
                CookieDough.getSessionFactory().createEntityManager());

        // Créer l'interface de sélection des kits améliorée
        ImprovedKitSelectionUI kitSelectionUI = new ImprovedKitSelectionUI(KitManager.getInstance(),
                progressionService);
        getServer().getPluginManager().registerEvents(kitSelectionUI, this);

        // Enregistrer le listener pour le cookie de sélection de kit
        getServer().getPluginManager().registerEvents(new KitSelectorListener(kitSelectionUI, progressionService),
                this);

        // Enregistrer les commandes
        getCommand("kit").setExecutor(new KitCommand(progressionService));
    }
}