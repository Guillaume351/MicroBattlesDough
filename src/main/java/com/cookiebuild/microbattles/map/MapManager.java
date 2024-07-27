package com.cookiebuild.microbattles.map;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.utils.ZipUtils;
import com.cookiebuild.microbattles.MicroBattles;
import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MapManager {

    private static final Map<String, GameMap> maps = new HashMap<>();

    public static void addMap(GameMap map) {
        MapManager.maps.put(map.getName(), map);
    }

    public static GameMap getMap(String name) {
        return MapManager.maps.get(name);
    }

    public static void removeMap(String name) {
        MapManager.maps.remove(name);
    }

    public static GameMap loadMapForGame(UUID gameUUID, String mapName) throws IOException {
        GameMap map = maps.get(mapName);
        if (map == null) {
            throw new IllegalArgumentException("Map " + mapName + " does not exist.");
        }

        File zippedMap = new File("mb_maps", mapName + ".zip");   // your maps are zipped
        File gameMapDir = new File("game_maps", gameUUID.toString());
        ZipUtils.unzip(zippedMap, gameMapDir);
        // once unzipped, you can load the map from the gameMapDir
        // use api to load
        CookieDough.getInstance().getLogger().info("Loaded map " + mapName);
        WorldCreator worldCreator = new WorldCreator(gameMapDir.getName());

        Bukkit.createWorld(worldCreator);

        return new GameMap(mapName);  // You might need to pass more params to GameMap constructor if it requires it.
    }

    private static void loadGameMaps() {
        MicroBattles.getInstance().getLogger().info("Loading game maps...");
        for (String mapName : Objects.requireNonNull(MicroBattles.getInstance().getConfig().getConfigurationSection("maps")).getKeys(false)) {
            GameMap map = new GameMap(mapName);  // Assuming simple initialization of GameMap
            maps.put(mapName, map);
            MicroBattles.getInstance().getLogger().info("Loaded map " + mapName);
        }
    }
}
