package com.cookiebuild.microbattles.listener;

import org.bukkit.Material;

final class KitSelectorOwnership {
    private KitSelectorOwnership() {
    }

    static boolean isMicroBattlesSelector(Material material, boolean hasMarker) {
        return material == Material.COOKIE && hasMarker;
    }
}
