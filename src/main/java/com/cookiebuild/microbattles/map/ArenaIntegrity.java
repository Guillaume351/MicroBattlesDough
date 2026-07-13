package com.cookiebuild.microbattles.map;

import java.util.List;

/** Pure policy for rejecting arena templates that would strand players in void. */
final class ArenaIntegrity {
    static final int REQUIRED_TEAM_SPAWNS = 4;
    static final int MIN_NEARBY_SOLID_BLOCKS = 16;

    record SpawnSample(boolean hasGround, int nearbySolidBlocks) {
    }

    private ArenaIntegrity() {
    }

    static void validate(String mapName, List<SpawnSample> samples) {
        if (samples.size() != REQUIRED_TEAM_SPAWNS) {
            throw new IllegalStateException("Map " + mapName + " did not load all four team spawns");
        }
        for (int index = 0; index < samples.size(); index++) {
            SpawnSample sample = samples.get(index);
            if (!sample.hasGround()) {
                throw new IllegalStateException("Map " + mapName + " team spawn " + index
                        + " has no solid ground within four blocks");
            }
            if (sample.nearbySolidBlocks() < MIN_NEARBY_SOLID_BLOCKS) {
                throw new IllegalStateException("Map " + mapName + " team spawn " + index
                        + " appears empty (only " + sample.nearbySolidBlocks() + " nearby solid blocks)");
            }
        }
    }
}
