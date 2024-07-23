package com.cookiebuild.microbattles.map;

import com.cookiebuild.microbattles.MicroBattles;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

    private static void loadGameMaps() {
        // Fetch list of map names from plugin's config
        // Load each map
        MicroBattles.getInstance().getLogger().info("Loading game maps...");
        for (String mapName : Objects.requireNonNull(MicroBattles.getInstance().getConfig().getConfigurationSection("maps")).getKeys(false)) {
            GameMap map = maps.get(mapName);
            if (map == null) {
                map = new GameMap(mapName);
            }
            maps.put(mapName, map);
            MicroBattles.getInstance().getLogger().info("Loaded map " + mapName);
        }
    }
}
