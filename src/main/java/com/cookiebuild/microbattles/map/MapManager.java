package com.cookiebuild.microbattles.map;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.ArenaPreparationPipeline.WorldLoad;
import com.cookiebuild.cookiedough.game.ArenaPreparationPipeline.PreparedFilesInUseException;
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
import io.papermc.paper.math.Position;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class MapManager {
    private static final Logger LOGGER = Logger.getLogger(MapManager.class.getName());
    private static final long MAIN_THREAD_BUDGET_MILLIS = 250L;
    private static final int TERRAIN_SAMPLE_RADIUS = 4;
    private static final int WALL_SEED_SEARCH_RADIUS = 3;

    public record PreparedMap(UUID gameId, String mapName, NamespacedKey worldKey,
            File archive, File destination, int sanitizedBlockEntities) { }

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
        PreparedMap prepared = prepareIo(plan(gameUUID, mapName));
        try {
            return loadPrepared(prepared);
        } catch (IOException error) {
            discardPrepared(prepared);
            throw error;
        }
    }

    public static PreparedMap plan(UUID gameUUID, String mapName) throws IOException {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MicroBattles map plans must be created on the server thread");
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
        return new PreparedMap(gameUUID, mapName, worldKey, zippedMap, gameMapDir, 0);
    }

    public static PreparedMap prepareIo(PreparedMap prepared) throws IOException {
        File gameMapDir = prepared.destination();
        if (gameMapDir.exists()) {
            FileUtils.deleteDirectory(gameMapDir);
        }
        try {
            ZipUtils.unzip(prepared.archive(), gameMapDir);
            Files.deleteIfExists(gameMapDir.toPath().resolve("uid.dat"));
            Files.deleteIfExists(gameMapDir.toPath().resolve("session.lock"));
            int sanitized = AnvilBlockEntitySanitizer.sanitizeKnownMap(
                    prepared.mapName(), gameMapDir.toPath());
            if (!gameMapDir.isDirectory()) {
                throw new IOException("Unzipped world folder does not exist: " + gameMapDir.getAbsolutePath());
            }
            return new PreparedMap(prepared.gameId(), prepared.mapName(), prepared.worldKey(),
                    prepared.archive(), prepared.destination(), sanitized);
        } catch (IOException | RuntimeException error) {
            discardPrepared(prepared);
            if (error instanceof IOException ioError) throw ioError;
            throw new IOException("Could not prepare MicroBattles map", error);
        }
    }

    public static GameMap loadPrepared(PreparedMap prepared) throws IOException {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MicroBattles worlds must be loaded on the server thread");
        }
        NamespacedKey worldKey = prepared.worldKey();
        try {
            World world = createPreparedWorld(prepared);
            return initializePreparedMap(prepared, world);
        } catch (IOException | RuntimeException error) {
            World partial = Bukkit.getWorld(worldKey);
            if (partial != null && partial.getPlayers().isEmpty()) Bukkit.unloadWorld(partial, false);
            if (error instanceof IOException ioError) throw ioError;
            throw new IOException("Failed to initialize world " + worldKey, error);
        }
    }

    /** Preloads every chunk intersecting the spawn integrity samples before block reads. */
    public static WorldLoad<GameMap> loadPreparedAsync(PreparedMap prepared) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MicroBattles worlds must be loaded on the server thread");
        }
        CompletableFuture<GameMap> result = new CompletableFuture<>();
        final World world;
        long createStartedAt = System.nanoTime();
        try {
            world = createPreparedWorld(prepared);
        } catch (Throwable error) {
            failAsyncLoad(prepared, result, error, elapsedMillis(createStartedAt), 0L, 0L);
            return WorldLoad.nonCancellable(result);
        }
        long createWorldMillis = elapsedMillis(createStartedAt);

        Set<ChunkCoordinate> chunks = validationChunks(prepared.mapName());
        long preloadStartedAt = System.nanoTime();
        CompletableFuture<?>[] chunkLoads;
        try {
            chunkLoads = chunks.stream()
                    .map(chunk -> world.getChunkAtAsync(chunk.x(), chunk.z(), true))
                    .toArray(CompletableFuture<?>[]::new);
        } catch (Throwable error) {
            failAsyncLoad(prepared, result, error, createWorldMillis,
                    elapsedMillis(preloadStartedAt), 0L);
            return WorldLoad.nonCancellable(result);
        }

        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<Void> aggregate = CompletableFuture.allOf(chunkLoads);
        aggregate.whenComplete((ignored, preloadError) -> {
            if (cancelled.get()) return;
            if (!Bukkit.isPrimaryThread()) {
                result.completeExceptionally(new PreparedFilesInUseException(
                        "Paper completed MicroBattles chunks off the server thread",
                        new IllegalStateException("Async chunk completion violated the Paper contract")));
                return;
            }
            long chunkPreloadMillis = elapsedMillis(preloadStartedAt);
            if (preloadError != null) {
                failAsyncLoad(prepared, result,
                        new IOException("Could not preload MicroBattles validation chunks", preloadError),
                        createWorldMillis, chunkPreloadMillis, 0L);
                return;
            }

            long validationStartedAt = System.nanoTime();
            try {
                GameMap map = initializePreparedMap(prepared, world);
                long validationMillis = elapsedMillis(validationStartedAt);
                logAsyncLoadTimings(prepared, createWorldMillis, chunkPreloadMillis, validationMillis, null);
                result.complete(map);
            } catch (Throwable error) {
                failAsyncLoad(prepared, result, error, createWorldMillis, chunkPreloadMillis,
                        elapsedMillis(validationStartedAt));
            }
        });
        return WorldLoad.cancellable(result,
                () -> cancelAsyncLoad(prepared, result, aggregate, chunkLoads, cancelled));
    }

    private static World createPreparedWorld(PreparedMap prepared) throws IOException {
        String mapName = prepared.mapName();
        NamespacedKey worldKey = prepared.worldKey();
        if (prepared.sanitizedBlockEntities() > 0) {
            MicroBattles.getInstance().getLogger().warning("Removed " + prepared.sanitizedBlockEntities()
                    + " orphaned block entities from extracted map " + mapName);
        }
        Location forcedSpawn = getTeamSpawnsForMap(mapName, null).getFirst();
        World world;
        try {
            world = WorldCreator.ofKey(worldKey)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false)
                    // Some legacy maps have stale/missing spawn metadata. Supplying
                    // a validated team spawn prevents Paper's synchronous spawn search.
                    .forcedSpawnPosition(Position.block(
                            forcedSpawn.getBlockX(), forcedSpawn.getBlockY(), forcedSpawn.getBlockZ()),
                            forcedSpawn.getYaw(), forcedSpawn.getPitch())
                    .generator(new VoidChunkGenerator())
                    .createWorld();
        } catch (RuntimeException error) {
            throw new IOException("Failed to create world: " + worldKey, error);
        }
        if (world == null) throw new IOException("Failed to create world: " + worldKey);

        Path expected = prepared.destination().toPath().toAbsolutePath().normalize();
        Path actual = world.getWorldFolder().toPath().toAbsolutePath().normalize();
        if (!actual.equals(expected)) {
            Bukkit.unloadWorld(world, false);
            throw new IOException("Paper resolved world " + worldKey + " to " + actual
                    + " instead of the prepared template directory " + expected);
        }
        CookieDough.getInstance().getLogger().info("Created world " + world.getKey() + " based on map " + mapName);
        world.setAutoSave(false);
        world.setThundering(false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        return world;
    }

    private static GameMap initializePreparedMap(PreparedMap prepared, World world) {
        GameMap map = new GameMap(prepared.mapName(), world);
        List<Location> teamSpawns = getTeamSpawnsForMap(prepared.mapName(), world);
        for (int i = 0; i < teamSpawns.size(); i++) map.setTeamSpawn(i, teamSpawns.get(i));
        map.validateArenaIntegrity();
        MicroBattles.getInstance().getLogger().info("Validated arena terrain for map " + prepared.mapName());
        loadedMaps.put(prepared.worldKey(), map);
        inGamePlayerEventListener.addProtectedWorld(world.getName());
        return map;
    }

    private static Set<ChunkCoordinate> validationChunks(String mapName) {
        Set<ChunkCoordinate> chunks = new LinkedHashSet<>();
        for (Location spawn : getTeamSpawnsForMap(mapName, null)) {
            int minChunkX = Math.floorDiv(spawn.getBlockX() - TERRAIN_SAMPLE_RADIUS, 16);
            int maxChunkX = Math.floorDiv(spawn.getBlockX() + TERRAIN_SAMPLE_RADIUS, 16);
            int minChunkZ = Math.floorDiv(spawn.getBlockZ() - TERRAIN_SAMPLE_RADIUS, 16);
            int maxChunkZ = Math.floorDiv(spawn.getBlockZ() + TERRAIN_SAMPLE_RADIUS, 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.add(new ChunkCoordinate(chunkX, chunkZ));
                }
            }
        }
        int[] wallCoordinates = getWallCoordinatesForMap(mapName);
        for (int index = 2; index < wallCoordinates.length; index += 2) {
            int seedX = wallCoordinates[index];
            int seedZ = wallCoordinates[index + 1];
            int minChunkX = Math.floorDiv(seedX - WALL_SEED_SEARCH_RADIUS, 16);
            int maxChunkX = Math.floorDiv(seedX + WALL_SEED_SEARCH_RADIUS, 16);
            int minChunkZ = Math.floorDiv(seedZ - WALL_SEED_SEARCH_RADIUS, 16);
            int maxChunkZ = Math.floorDiv(seedZ + WALL_SEED_SEARCH_RADIUS, 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.add(new ChunkCoordinate(chunkX, chunkZ));
                }
            }
        }
        return chunks;
    }

    private static boolean cancelAsyncLoad(PreparedMap prepared, CompletableFuture<GameMap> result,
            CompletableFuture<Void> aggregate, CompletableFuture<?>[] chunkLoads, AtomicBoolean cancelled) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MicroBattles world loads must be cancelled on the server thread");
        }
        if (!cancelled.compareAndSet(false, true)) return result.isDone();
        aggregate.cancel(false);
        for (CompletableFuture<?> chunkLoad : chunkLoads) chunkLoad.cancel(false);

        CancellationException cancellation = new CancellationException(
                "MicroBattles arena load cancelled for " + prepared.gameId());
        boolean unloaded = unloadPartialWorld(prepared);
        Throwable completionError = unloaded ? cancellation : new PreparedFilesInUseException(
                "Could not unload cancelled MicroBattles world " + prepared.worldKey(), cancellation);
        result.completeExceptionally(completionError);
        return unloaded && result.isDone();
    }

    private static void failAsyncLoad(PreparedMap prepared, CompletableFuture<GameMap> result,
            Throwable error, long createWorldMillis, long chunkPreloadMillis, long validationMillis) {
        boolean unloaded = unloadPartialWorld(prepared);
        Throwable completionError = unloaded ? error : new PreparedFilesInUseException(
                "Could not unload failed MicroBattles world " + prepared.worldKey(), error);
        logAsyncLoadTimings(prepared, createWorldMillis, chunkPreloadMillis, validationMillis, completionError);
        result.completeExceptionally(completionError);
    }

    private static boolean unloadPartialWorld(PreparedMap prepared) {
        GameMap registered = loadedMaps.remove(prepared.worldKey());
        World partial = Bukkit.getWorld(prepared.worldKey());
        String worldName = partial != null ? partial.getName()
                : registered == null ? null : registered.getWorld().getName();
        if (worldName != null && inGamePlayerEventListener != null) {
            inGamePlayerEventListener.removeProtectedWorld(worldName);
        }
        if (partial != null && (!partial.getPlayers().isEmpty() || !Bukkit.unloadWorld(partial, false))) {
            return false;
        }
        return true;
    }

    private static void logAsyncLoadTimings(PreparedMap prepared, long createWorldMillis,
            long chunkPreloadMillis, long validationMillis, Throwable failure) {
        String timings = "MicroBattles arena " + prepared.gameId()
                + " (create_world_ms=" + createWorldMillis
                + ", chunk_preload_ms=" + chunkPreloadMillis
                + ", validation_ms=" + validationMillis + ")";
        if (failure == null) LOGGER.info("Prepared " + timings);
        else LOGGER.warning("Failed to prepare " + timings + ": " + failure.getMessage());
        if (createWorldMillis > MAIN_THREAD_BUDGET_MILLIS) {
            LOGGER.warning("MicroBattles world creation exceeded the " + MAIN_THREAD_BUDGET_MILLIS
                    + "ms main-thread budget (create_world_ms=" + createWorldMillis + ")");
        }
        if (validationMillis > MAIN_THREAD_BUDGET_MILLIS) {
            LOGGER.warning("MicroBattles validation exceeded the " + MAIN_THREAD_BUDGET_MILLIS
                    + "ms main-thread budget (validation_ms=" + validationMillis + ")");
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private record ChunkCoordinate(int x, int z) { }

    public static void discardPrepared(PreparedMap prepared) {
        try {
            if (prepared.destination().exists()) FileUtils.deleteDirectory(prepared.destination());
        } catch (IOException error) {
            LOGGER.warning("Could not delete prepared MicroBattles files: " + error.getMessage());
        }
    }

    /** Unload a world registered by loadPrepared when game construction fails; filesystem cleanup stays async. */
    public static boolean discardLoadedWorld(PreparedMap prepared) {
        GameMap map = loadedMaps.get(prepared.worldKey());
        if (map == null) return true;
        World world = Bukkit.getWorld(map.getWorld().getKey());
        if (world != null && (!world.getPlayers().isEmpty() || !Bukkit.unloadWorld(world, false))) return false;
        loadedMaps.remove(prepared.worldKey(), map);
        if (inGamePlayerEventListener != null) {
            inGamePlayerEventListener.removeProtectedWorld(map.getWorld().getName());
        }
        return true;
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
