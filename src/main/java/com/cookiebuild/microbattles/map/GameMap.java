package com.cookiebuild.microbattles.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;

public class GameMap {

    private String name;
    private final Map<Integer, Location> teamSpawns;
    private final Map<Location, Material> wallBlocks;

    public GameMap(String name) {
        this.name = name;
        this.teamSpawns = new HashMap<>();
        this.wallBlocks = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTeamSpawn(int teamNumber, Location spawnLocation) {
        teamSpawns.put(teamNumber, spawnLocation);
    }

    public Location getTeamSpawn(int teamNumber) {
        return teamSpawns.get(teamNumber);
    }

    public void removeTeamSpawn(int teamNumber) {
        teamSpawns.remove(teamNumber);
    }

    public void addWallBlock(Location location, Material material) {
        wallBlocks.put(location, material);
    }

    public void removeWallBlock(Location location) {
        wallBlocks.remove(location);
    }


    public void removeWalls(World world) {
        for (Location location : wallBlocks.keySet()) {
            Block block = world.getBlockAt(location);
            block.setType(Material.AIR);
        }
        wallBlocks.clear();
    }

    public void setWallCoordinates(int[] coord, World world) {
        int y = coord[1];
        int x1Start = coord[2];
        int z1 = coord[3];
        int x1End = coord[4];
        int x2 = coord[5];
        int z2Start = coord[6];
        int z2End = coord[7];

        while (y >= coord[1] - 3) {
            int currentX1Start = x1Start;
            while (currentX1Start >= x1End) {
                Location loc = new Location(world, currentX1Start, y, z1);
                addWallBlock(loc, Material.STONE); // Assuming walls are made of stone
                currentX1Start -= 1;
            }
            int currentZ2Start = z2Start;
            while (currentZ2Start >= z2End) {
                Location loc = new Location(world, x2, y, currentZ2Start);
                addWallBlock(loc, Material.STONE); // Assuming walls are made of stone
                currentZ2Start -= 1;
            }
            y -= 1;
        }
    }
}