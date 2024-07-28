package com.cookiebuild.microbattles.map;

import org.bukkit.Location;
import org.bukkit.Material;
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

    public void removeWall() {
        for (Block block : wallBlocks) {
            block.setType(Material.AIR);
        }
        wallBlocks.clear();
    }
}