package com.cookiebuild.microbattles.map;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.utils.ZipUtils;
import com.cookiebuild.microbattles.MicroBattles;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MapManager {

    private static final Map<String, GameMap> maps = new HashMap<>();

    private static final int[][] WALL_COORDINATES = {
            {1, 18, 171, 149, 127, 149, 171, 127},
            {2, 18, 175, 134, 131, 153, 156, 112},
            {3, 19, 125, 129, 81, 103, 151, 107},
            {4, 18, 169, 102, 125, 147, 124, 80},
            {5, 44, 22, 0, -22, 0, -22, 22},
            {6, 24, 110, 128, 144, 127, 111, 145},
            {7, 43, -23, 0, 23, 0, -23, 23},
            {8, 22, 128, 119, 96, 112, 103, 136}
    };

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
        // use API to load
        CookieDough.getInstance().getLogger().info("Loaded map " + mapName);
        WorldCreator worldCreator = new WorldCreator(gameMapDir.getName());

        World world = Bukkit.createWorld(worldCreator);

        // Recreate the game map with the world loaded
        map.setWallCoordinates(getWallCoordinatesForMap(mapName), world);

        return map;  // You might need to pass more parameters to GameMap constructor if it requires it.
    }

    public static void loadGameMaps() {
        MicroBattles.getInstance().getLogger().info("Loading game maps...");
        // for now, use game-X (1 to 8) for maps


        for (String mapName : Objects.requireNonNull(MicroBattles.getInstance().getConfig().getConfigurationSection("maps")).getKeys(false)) {
            GameMap map = initializeMapWithCoordinates(mapName);  // Initialize GameMap with coordinates
            maps.put(mapName, map);
            MicroBattles.getInstance().getLogger().info("Loaded map " + mapName);
        }
    }

    private static GameMap initializeMapWithCoordinates(String mapName) {
        World world = Bukkit.getWorld(mapName); // Placeholder, replace with actual way to get world instance
        GameMap gameMap = new GameMap(mapName);
        gameMap.setWallCoordinates(getWallCoordinatesForMap(mapName), world);
        return gameMap;
    }

    private static int[] getWallCoordinatesForMap(String mapName) {
        int mapNumber = Integer.parseInt(mapName.replace("game-", ""));
        return WALL_COORDINATES[mapNumber - 1];
    }
}