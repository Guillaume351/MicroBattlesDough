package com.cookiebuild.microbattles.map;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.utils.FileUtils;
import com.cookiebuild.cookiedough.utils.ZipUtils;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.listener.InGamePlayerEventListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MapManager {

    public static InGamePlayerEventListener inGamePlayerEventListener;

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

        File zippedMap = new File("mb_maps", mapName + ".zip");
        File gameMapDir = new File("game_maps", gameUUID.toString());
        ZipUtils.unzip(zippedMap, gameMapDir);

        if (!gameMapDir.exists()) {
            throw new IOException("Unzipped world folder does not exist: " + gameMapDir.getAbsolutePath());
        }

        String worldName = gameUUID.toString();
        ZipUtils.unzip(zippedMap, gameMapDir);

        World world = new WorldCreator(gameMapDir.getPath())
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .generator(new VoidChunkGenerator())
                .createWorld();

        if (world == null) {
            throw new IOException("Failed to create world: " + worldName);
        }

        CookieDough.getInstance().getLogger().info("Created world " + world.getName() + " base on map " + mapName);
        world.setAutoSave(false);
        world.setThundering(false);

        // Copy map data to the newly created world
        File worldFolder = world.getWorldFolder();
        FileUtils.copyDirectory(gameMapDir, worldFolder);

        // Recreate the game map with the world loaded
        map = new GameMap("game_maps/" + gameUUID);

        // Set team spawns
        List<Location> teamSpawns = getTeamSpawnsForMap(mapName, world);
        for (int i = 0; i < teamSpawns.size(); i++) {
            map.setTeamSpawn(i, teamSpawns.get(i));
        }

        inGamePlayerEventListener.addProtectedWorld(world.getName());

        return map;
    }

    public static void loadGameMaps() {
        MicroBattles.getInstance().getLogger().info("Loading game maps...");

        for (String mapName : Objects.requireNonNull(MicroBattles.getInstance().getConfig().getConfigurationSection("maps")).getKeys(false)) {
            GameMap map = initializeMapWithCoordinates(mapName);
            maps.put(mapName, map);
            MicroBattles.getInstance().getLogger().info("Loaded map " + mapName);
        }
    }

    private static GameMap initializeMapWithCoordinates(String mapName) {

        GameMap gameMap = new GameMap(mapName);
        World world = Bukkit.getWorld(mapName); // Placeholder, replace with actual way to get world instance

        // TODO: Load wall coordinates from config

        // Get team spawns from config
        List<Location> teamSpawns = getTeamSpawnsForMap(mapName, world);
        for (int i = 0; i < teamSpawns.size(); i++) {
            gameMap.setTeamSpawn(i, teamSpawns.get(i));
        }

        return gameMap;
    }

    private static List<Location> getTeamSpawnsForMap(String mapName, World world) {
        ConfigurationSection mapSection = MicroBattles.getInstance().getConfig().getConfigurationSection("maps")
                .getConfigurationSection(mapName);
        List<?> teamSpawnsList = mapSection.getList("team-spawns");

        List<Location> teamSpawns = new ArrayList<>();
        for (Object location : teamSpawnsList) {
            if (location instanceof List<?> coords) {
                if (coords.size() == 3 && coords.get(0) instanceof Number && coords.get(1) instanceof Number && coords.get(2) instanceof Number) {
                    double x = ((Number) coords.get(0)).doubleValue();
                    double y = ((Number) coords.get(1)).doubleValue();
                    double z = ((Number) coords.get(2)).doubleValue();
                    teamSpawns.add(new Location(world, x, y, z));
                }
            }
        }

        return teamSpawns;
    }

    public static int[] getWallCoordinatesForMap(String mapName) {
        int mapNumber = Integer.parseInt(mapName.replace("game-", ""));
        return WALL_COORDINATES[mapNumber - 1];
    }

    private static class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }

    public static String getRandomMapName() {
        ArrayList<String> mapNames = new ArrayList<>(maps.keySet());
        return mapNames.get(new Random().nextInt(mapNames.size()));
    }
}