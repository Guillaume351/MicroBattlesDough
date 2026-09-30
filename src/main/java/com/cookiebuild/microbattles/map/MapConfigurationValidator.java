package com.cookiebuild.microbattles.map;

import java.util.List;

/** Pure validation for map configuration, kept independent from Bukkit for tests. */
public final class MapConfigurationValidator {
    public static final int TEAM_COUNT = 4;
    /** Historical hard-coded fall threshold, kept for maps without {@code void-y}. */
    public static final int DEFAULT_VOID_Y = 0;

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

    /**
     * Resolves the optional {@code void-y} level: a player below it is treated as fallen.
     * Defaults to {@link #DEFAULT_VOID_Y} and must stay below every team spawn.
     */
    public static int resolveVoidY(String mapName, Object configured, List<?> teamSpawns) {
        if (configured == null) {
            return DEFAULT_VOID_Y;
        }
        if (!(configured instanceof Number number) || number.doubleValue() != Math.floor(number.doubleValue())) {
            throw new IllegalArgumentException("Map " + mapName + " has a non-integer void-y");
        }
        int voidY = number.intValue();
        for (Object value : teamSpawns) {
            if (value instanceof List<?> coordinates && coordinates.size() == 3
                    && coordinates.get(1) instanceof Number spawnY && spawnY.doubleValue() <= voidY) {
                throw new IllegalArgumentException("Map " + mapName + " void-y " + voidY
                        + " must be below every team spawn");
            }
        }
        return voidY;
    }
}
