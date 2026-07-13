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
import java.util.List;

public class GameMap {
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
        for (int i = 2; i < coordinates.length; i += 2) {
            Block block = getWorld().getBlockAt(coordinates[i], y, coordinates[i + 1]);
            if (block.getType() == Material.GLASS_PANE) {
                wallBlocks.add(block);
            }
        }
    }

    private List<Block> getAllGlassPanes() {
        List<Block> blocks = new ArrayList<>();
        for (Chunk chunk : getWorld().getLoadedChunks()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = getWorld().getMinHeight(); y < getWorld().getMaxHeight(); y++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getType() == Material.GLASS_PANE) {
                            blocks.add(block);
                        }
                    }
                }
            }
        }
        return blocks;
    }

    public void removeWall() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(MicroBattles.getInstance(), this::removeWall);
            return;
        }

        List<Block> glassBlocks = getAllGlassPanes();
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
