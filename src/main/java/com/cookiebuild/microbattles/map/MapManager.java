package com.cookiebuild.microbattles.map;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.utils.FileUtils;
import com.cookiebuild.cookiedough.utils.ZipUtils;
import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.listener.InGamePlayerEventListener;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.ArrayDeque;

public class MapManager {

    public static InGamePlayerEventListener inGamePlayerEventListener;

    private static final Map<String, GameMap> maps = new HashMap<>();
    private static final Map<NamespacedKey, GameMap> loadedMaps = new HashMap<>();
    private static final ArrayDeque<String> recentMaps = new ArrayDeque<>();
    private static final NextMapVote nextMapVote = new NextMapVote();

    public static void addMap(GameMap map) {
        maps.put(map.getName(), map);
    }

    public static GameMap getMap(String name) {
        return maps.get(name);
    }

    public static void removeMap(String name) {
        maps.remove(name);
    }

    public static GameMap loadMapForGame(UUID gameUUID, String mapName) throws IOException {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MicroBattles worlds must be loaded on the server thread");
        }
        if (!maps.containsKey(mapName)) {
            throw new IllegalArgumentException("Map " + mapName + " does not exist.");
        }

        File zippedMap = new File("mb_maps", mapName + ".zip");
        if (!zippedMap.isFile()) {
            throw new IOException("Map archive does not exist: " + zippedMap.getAbsolutePath());
        }

        NamespacedKey worldKey = new NamespacedKey(MicroBattles.getInstance(), "match_" + gameUUID);
        File gameMapDir = getWorldFolder(worldKey).toFile();
        if (gameMapDir.exists()) {
            FileUtils.deleteDirectory(gameMapDir);
        }
        ZipUtils.unzip(zippedMap, gameMapDir);
        Files.deleteIfExists(gameMapDir.toPath().resolve("uid.dat"));
        Files.deleteIfExists(gameMapDir.toPath().resolve("session.lock"));

        if (!gameMapDir.isDirectory()) {
            throw new IOException("Unzipped world folder does not exist: " + gameMapDir.getAbsolutePath());
        }

        World world = WorldCreator.ofKey(worldKey)
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .generator(new VoidChunkGenerator())
                .createWorld();

        if (world == null) {
            FileUtils.deleteDirectory(gameMapDir);
            throw new IOException("Failed to create world: " + worldKey);
        }

        Path expectedWorldFolder = gameMapDir.toPath().toAbsolutePath().normalize();
        Path actualWorldFolder = world.getWorldFolder().toPath().toAbsolutePath().normalize();
        if (!actualWorldFolder.equals(expectedWorldFolder)) {
            if (Bukkit.unloadWorld(world, false)) {
                FileUtils.deleteDirectory(actualWorldFolder.toFile());
            }
            FileUtils.deleteDirectory(expectedWorldFolder.toFile());
            throw new IOException("Paper resolved world " + worldKey + " to " + actualWorldFolder
                    + " instead of the prepared template directory " + expectedWorldFolder);
        }

        try {
            CookieDough.getInstance().getLogger()
                    .info("Created world " + world.getKey() + " based on map " + mapName);
            world.setAutoSave(false);
            world.setThundering(false);
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);

            GameMap map = new GameMap(mapName, world);
            List<Location> teamSpawns = getTeamSpawnsForMap(mapName, world);
            for (int i = 0; i < teamSpawns.size(); i++) {
                map.setTeamSpawn(i, teamSpawns.get(i));
            }
            map.validateArenaIntegrity();
            MicroBattles.getInstance().getLogger().info("Validated arena terrain for map " + mapName);

            loadedMaps.put(worldKey, map);
            inGamePlayerEventListener.addProtectedWorld(world.getName());
            return map;
        } catch (RuntimeException e) {
            if (Bukkit.unloadWorld(world, false)) {
                FileUtils.deleteDirectory(gameMapDir);
            }
            throw new IOException("Failed to initialize world " + worldKey, e);
        }
    }

    private static Path getWorldFolder(NamespacedKey worldKey) throws IOException {
        World overworld = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        if (overworld == null) {
            throw new IOException("The primary overworld must be loaded before MicroBattles maps");
        }

        Path overworldFolder = overworld.getWorldFolder().toPath().toAbsolutePath().normalize();
        Path dimensionsRoot = WorldFolderResolver.resolveLevelFolder(overworldFolder).resolve("dimensions").normalize();
        Path worldFolder = dimensionsRoot.resolve(worldKey.getNamespace()).resolve(worldKey.getKey()).normalize();
        if (!worldFolder.startsWith(dimensionsRoot)) {
            throw new IOException("Invalid world key path: " + worldKey);
        }
        return worldFolder;
    }

    public static boolean unloadMap(GameMap map) {
        if (map == null) {
            return true;
        }
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MicroBattles worlds must be unloaded on the server thread");
        }

        World world = map.getWorld();
        File worldFolder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            MicroBattles.getInstance().getLogger().warning("Could not unload world " + world.getKey()
                    + "; its files were left untouched");
            return false;
        }

        loadedMaps.remove(world.getKey());
        inGamePlayerEventListener.removeProtectedWorld(world.getName());
        try {
            FileUtils.deleteDirectory(worldFolder);
            return true;
        } catch (IOException e) {
            MicroBattles.getInstance().getLogger().severe("Unloaded " + world.getKey()
                    + " but could not delete " + worldFolder + ": " + e.getMessage());
            return false;
        }
    }

    public static void cleanupLoadedMaps() {
        for (GameMap map : new ArrayList<>(loadedMaps.values())) {
            unloadMap(map);
        }
    }

    public static void loadGameMaps() {
        MicroBattles.getInstance().getLogger().info("Loading game maps...");

        ConfigurationSection mapsSection = Objects.requireNonNull(
                MicroBattles.getInstance().getConfig().getConfigurationSection("maps"),
                "Missing maps section in config.yml");
        for (String mapName : mapsSection.getKeys(false)) {
            ConfigurationSection mapSection = Objects.requireNonNull(mapsSection.getConfigurationSection(mapName),
                    "Missing configuration for map " + mapName);
            MapConfigurationValidator.validate(mapName, mapSection.getList("team-spawns"),
                    mapSection.getList("wall-coordinates"));
            maps.put(mapName, new GameMap(mapName));
            MicroBattles.getInstance().getLogger().info("Registered map " + mapName);
        }
        nextMapVote.configure(maps.keySet());
    }

    private static List<Location> getTeamSpawnsForMap(String mapName, World world) {
        ConfigurationSection mapsSection = Objects.requireNonNull(
                MicroBattles.getInstance().getConfig().getConfigurationSection("maps"),
                "Missing maps section in config.yml");
        ConfigurationSection mapSection = Objects.requireNonNull(mapsSection.getConfigurationSection(mapName),
                "Missing configuration for map " + mapName);
        List<?> teamSpawnsList = Objects.requireNonNull(mapSection.getList("team-spawns"),
                "Missing team spawns for map " + mapName);

        List<Location> teamSpawns = new ArrayList<>();
        for (Object location : teamSpawnsList) {
            if (location instanceof List<?> coords && coords.size() == 3
                    && coords.get(0) instanceof Number && coords.get(1) instanceof Number
                    && coords.get(2) instanceof Number) {
                double x = ((Number) coords.get(0)).doubleValue();
                double y = ((Number) coords.get(1)).doubleValue();
                double z = ((Number) coords.get(2)).doubleValue();
                teamSpawns.add(new Location(world, x, y, z));
            }
        }

        if (teamSpawns.size() != MapConfigurationValidator.TEAM_COUNT) {
            throw new IllegalArgumentException("Map " + mapName + " must have exactly four valid team spawns");
        }
        return teamSpawns;
    }

    public static int[] getWallCoordinatesForMap(String mapName) {
        List<Integer> coordinates = MicroBattles.getInstance().getConfig()
                .getIntegerList("maps." + mapName + ".wall-coordinates");
        if (coordinates.size() < 4 || coordinates.size() % 2 != 0) {
            throw new IllegalArgumentException("Invalid wall coordinates for map " + mapName);
        }
        return coordinates.stream().mapToInt(Integer::intValue).toArray();
    }

    private static class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }

    public static synchronized String getRandomMapName() {
        ArrayList<String> mapNames = new ArrayList<>(maps.keySet());
        if (mapNames.isEmpty()) {
            throw new IllegalStateException("No MicroBattles maps are configured");
        }
        String selected = nextMapVote.consumeWinner();
        if (selected == null || !maps.containsKey(selected)) {
            List<String> candidates = mapNames.stream().filter(name -> !recentMaps.contains(name)).toList();
            if (candidates.isEmpty()) {
                recentMaps.clear();
                candidates = mapNames;
            }
            selected = candidates.get(new Random().nextInt(candidates.size()));
        }
        recentMaps.addLast(selected);
        int historySize = Math.min(2, Math.max(0, mapNames.size() - 1));
        while (recentMaps.size() > historySize) {
            recentMaps.removeFirst();
        }
        return selected;
    }

    public static boolean voteForNextMap(UUID playerId, String mapName) {
        return nextMapVote.vote(playerId, mapName);
    }

    public static void removeNextMapVote(UUID playerId) {
        nextMapVote.removeVote(playerId);
    }

    public static Map<String, Long> getNextMapVoteTallies() {
        return nextMapVote.tallies();
    }

    public static List<String> getConfiguredMapNames() {
        return nextMapVote.availableMaps();
    }
}
