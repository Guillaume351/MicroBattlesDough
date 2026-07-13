package com.cookiebuild.microbattles.map;

import java.util.List;

/** Pure validation for map configuration, kept independent from Bukkit for tests. */
public final class MapConfigurationValidator {
    public static final int TEAM_COUNT = 4;

    private MapConfigurationValidator() {
    }

    public static void validate(String mapName, List<?> teamSpawns, List<?> wallCoordinates) {
        if (teamSpawns == null || teamSpawns.size() != TEAM_COUNT) {
            throw new IllegalArgumentException("Map " + mapName + " must define exactly " + TEAM_COUNT
                    + " team spawns");
        }
        for (int index = 0; index < teamSpawns.size(); index++) {
            Object value = teamSpawns.get(index);
            if (!(value instanceof List<?> coordinates) || coordinates.size() != 3
                    || coordinates.stream().anyMatch(coordinate -> !(coordinate instanceof Number))) {
                throw new IllegalArgumentException("Map " + mapName + " has an invalid team spawn at index "
                        + index + "; expected [x, y, z]");
            }
        }

        if (wallCoordinates == null || wallCoordinates.size() < 4 || wallCoordinates.size() % 2 != 0
                || wallCoordinates.stream().anyMatch(coordinate -> !(coordinate instanceof Number))) {
            throw new IllegalArgumentException("Map " + mapName
                    + " must define [legacy-id, y, x, z, ...] numeric wall coordinates");
        }
    }
}
