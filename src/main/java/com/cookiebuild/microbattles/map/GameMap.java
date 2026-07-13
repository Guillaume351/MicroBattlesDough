package com.cookiebuild.microbattles.map;

import com.cookiebuild.microbattles.MicroBattles;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameMap {
    private static final int WALL_SEED_SEARCH_RADIUS = 3;
    private final String name;
    private final World world;
    private final List<Location> teamSpawns;
    private final List<Block> wallBlocks = new ArrayList<>();

    public GameMap(String name) {
        this(name, null);
    }

    public GameMap(String name, World world) {
        this.name = name;
        this.world = world;
        this.teamSpawns = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public World getWorld() {
        if (world == null) {
            throw new IllegalStateException("Map template " + name + " does not have a loaded world");
        }
        return world;
    }

    public void setTeamSpawn(int teamNumber, Location spawn) {
        if (teamNumber >= teamSpawns.size()) {
            teamSpawns.add(spawn);
        } else {
            teamSpawns.set(teamNumber, spawn);
        }
    }

    public Location getTeamSpawn(int teamNumber) {
        if (teamNumber < 0 || teamNumber >= teamSpawns.size()) {
            throw new IllegalArgumentException("Invalid team number");
        }
        return teamSpawns.get(teamNumber);
    }

    public void identifyWallBlocks(int[] coordinates) {
        wallBlocks.clear();
        int y = coordinates[1];
        ArrayDeque<Block> frontier = new ArrayDeque<>();
        for (int i = 2; i < coordinates.length; i += 2) {
            Block block = findNearestWallSeed(coordinates[i], y, coordinates[i + 1]);
            if (block != null) {
                frontier.add(block);
            }
        }

        Set<Block> visited = new HashSet<>();
        while (!frontier.isEmpty()) {
            Block block = frontier.removeFirst();
            if (!visited.add(block) || block.getType() != Material.GLASS_PANE) {
                continue;
            }
            wallBlocks.add(block);
            for (org.bukkit.block.BlockFace face : List.of(
                    org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST,
                    org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN)) {
                Block adjacent = block.getRelative(face);
                if (!visited.contains(adjacent) && adjacent.getType() == Material.GLASS_PANE) {
                    frontier.addLast(adjacent);
                }
            }
        }
        if (wallBlocks.isEmpty()) {
            // Some legacy templates have stale seed coordinates. Preserve their playability
            // by using the old loaded-chunk discovery as a bounded compatibility fallback.
            for (Chunk chunk : getWorld().getLoadedChunks()) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int blockY = getWorld().getMinHeight(); blockY < getWorld().getMaxHeight(); blockY++) {
                            Block block = chunk.getBlock(x, blockY, z);
                            if (block.getType() == Material.GLASS_PANE) {
                                wallBlocks.add(block);
                            }
                        }
                    }
                }
            }
            if (!wallBlocks.isEmpty()) {
                MicroBattles.getInstance().getLogger().warning("Map " + name
                        + " uses legacy wall discovery; refresh its wall-coordinates when the map is next edited");
            }
        }
    }

    private Block findNearestWallSeed(int x, int y, int z) {
        Block exact = getWorld().getBlockAt(x, y, z);
        if (exact.getType() == Material.GLASS_PANE) {
            return exact;
        }

        Block nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (int dx = -WALL_SEED_SEARCH_RADIUS; dx <= WALL_SEED_SEARCH_RADIUS; dx++) {
            for (int dy = -WALL_SEED_SEARCH_RADIUS; dy <= WALL_SEED_SEARCH_RADIUS; dy++) {
                for (int dz = -WALL_SEED_SEARCH_RADIUS; dz <= WALL_SEED_SEARCH_RADIUS; dz++) {
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (distance == 0 || distance >= nearestDistance) {
                        continue;
                    }
                    Block candidate = getWorld().getBlockAt(x + dx, y + dy, z + dz);
                    if (candidate.getType() == Material.GLASS_PANE) {
                        nearest = candidate;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    public void removeWall() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(MicroBattles.getInstance(), this::removeWall);
            return;
        }

        List<Block> glassBlocks = new ArrayList<>(wallBlocks);
        new BukkitRunnable() {
            private static final int BATCH_SIZE = 100;
            private int index = 0;

            @Override
            public void run() {
                for (int i = 0; i < BATCH_SIZE && index < glassBlocks.size(); i++, index++) {
                    glassBlocks.get(index).setType(Material.AIR);
                }

                if (index >= glassBlocks.size()) {
                    cancel();
                    wallBlocks.clear();
                }
            }
        }.runTaskTimer(MicroBattles.getInstance(), 0L, 1L);
    }
}
