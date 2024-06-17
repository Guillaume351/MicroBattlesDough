package com.cookiebuild.microbattles.map;

import com.cookiebuild.microbattles.MicroBattles;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;

public class GameMap {

    private String name;
    private Map<Integer, Location> teamSpawns;
    private Map<Location, Material> wallBlocks;

    public GameMap(String name) {
        this.name = name;
        this.teamSpawns = new HashMap<>();
        this.wallBlocks = new HashMap<>();

        // TODO: load wall blocks & team spawns from map's config
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

}
