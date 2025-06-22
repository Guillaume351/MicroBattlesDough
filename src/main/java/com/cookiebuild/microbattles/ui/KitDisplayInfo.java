package com.cookiebuild.microbattles.ui;

import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitLevel;
import com.cookiebuild.microbattles.kits.TieredKit;

public class KitDisplayInfo {
    final String kitName;
    final int level;
    final boolean unlocked;
    final boolean hasLevel;
    final boolean canAfford;
    final Kit originalKit;
    final TieredKit tieredKit;
    final KitLevel kitLevel;

    // Constructor for the default kit
    KitDisplayInfo(String kitName, int level, boolean unlocked, boolean hasLevel, boolean canAfford, Kit originalKit) {
        this.kitName = kitName;
        this.level = level;
        this.unlocked = unlocked;
        this.hasLevel = hasLevel;
        this.canAfford = canAfford;
        this.originalKit = originalKit;
        this.tieredKit = null;
        this.kitLevel = null;
    }

    // Constructor for tiered kits
    KitDisplayInfo(String kitName, int level, boolean unlocked, boolean hasLevel, boolean canAfford,
            TieredKit tieredKit, KitLevel kitLevel) {
        this.kitName = kitName;
        this.level = level;
        this.unlocked = unlocked;
        this.hasLevel = hasLevel;
        this.canAfford = canAfford;
        this.originalKit = null;
        this.tieredKit = tieredKit;
        this.kitLevel = kitLevel;
    }
}