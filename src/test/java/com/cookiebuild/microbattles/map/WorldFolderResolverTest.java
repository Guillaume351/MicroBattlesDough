package com.cookiebuild.microbattles.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WorldFolderResolverTest {
    @Test
    void resolvesPaper26KeyedOverworldBackToTheLevelFolder() {
        Path keyedOverworld = Path.of("/srv/paper/lobby/dimensions/minecraft/overworld");

        assertEquals(Path.of("/srv/paper/lobby"), WorldFolderResolver.resolveLevelFolder(keyedOverworld));
    }

    @Test
    void preservesLegacyTopLevelOverworldFolder() {
        Path levelFolder = Path.of("/srv/paper/lobby");

        assertEquals(levelFolder, WorldFolderResolver.resolveLevelFolder(levelFolder));
    }
}
