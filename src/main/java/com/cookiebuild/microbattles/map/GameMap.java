package com.cookiebuild.microbattles.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

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

    public void removeWall() {
        for (Block block : wallBlocks) {
            block.setType(Material.AIR);
        }
        wallBlocks.clear();
    }
}