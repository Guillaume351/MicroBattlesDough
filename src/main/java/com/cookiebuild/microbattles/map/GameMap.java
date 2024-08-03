package com.cookiebuild.microbattles.map;

import com.cookiebuild.cookiedough.CookieDough;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GameMap {
    private final String name;
    private final List<Location> teamSpawns;
    private final List<Block> wallBlocks = new ArrayList<>();

    public GameMap(String name) {
        this.name = name;
        this.teamSpawns = new ArrayList<>();
    }

    public String getName() {
        return name;
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

    public void identifyWallBlocks(World world, int[] coordinates) {
        int y = coordinates[1];
        for (int i = 2; i < coordinates.length; i += 2) {
            int x = coordinates[i];
            int z = coordinates[i + 1];
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.GLASS_PANE) {
                wallBlocks.add(block);
            }
        }
    }

    public List<Block> getAllGlassPanes() {
        Chunk[] chunks = Bukkit.getWorld(name).getLoadedChunks();
        List<Block> blocks = new ArrayList<>();
        for (Chunk chunk : chunks) {
            // iterate over all blocks in the chunk
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 256; y++) {
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
        CompletableFuture.runAsync(() -> {
            List<Block> glassBlocks = getAllGlassPanes();

            new BukkitRunnable() {
                private final int batchSize = 100;
                private int index = 0;

                @Override
                public void run() {
                    for (int i = 0; i < batchSize && index < glassBlocks.size(); i++, index++) {
                        Block block = glassBlocks.get(index);
                        block.setType(Material.AIR);
                    }

                    if (index >= glassBlocks.size()) {
                        this.cancel();
                        wallBlocks.clear();
                    }
                }
            }.runTaskTimer(CookieDough.getInstance(), 0L, 1L);
        });
    }
}