package com.cookiebuild.microbattles.map;

import java.nio.file.Path;

/** Resolves both legacy and Paper 26 keyed-overworld layouts to the level root. */
final class WorldFolderResolver {
    private WorldFolderResolver() {
    }

    static Path resolveLevelFolder(Path overworldFolder) {
        Path normalized = overworldFolder.toAbsolutePath().normalize();
        Path keyedOverworldSuffix = Path.of("dimensions", "minecraft", "overworld");
        if (normalized.endsWith(keyedOverworldSuffix)) {
            return normalized.getParent().getParent().getParent();
        }
        return normalized;
    }
}
