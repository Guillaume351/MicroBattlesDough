package com.cookiebuild.microbattles.ui;

import org.bukkit.entity.Player;

import com.cookiebuild.microbattles.kits.Kit;
import com.cookiebuild.microbattles.kits.KitLevel;
import com.cookiebuild.microbattles.kits.TieredKit;

public class KitDisplayInfo {
    public final String kitName;
    public final int level;
    public final boolean unlocked;
    public final boolean hasLevel;
    public final boolean canAfford;
    public final TieredKit tieredKit;
    public final KitLevel kitLevel;
    public final Kit originalKit;
    public final Player player;

    public KitDisplayInfo(String kitName, int level, boolean unlocked, boolean hasLevel, boolean canAfford,
            TieredKit tieredKit, KitLevel kitLevel, Kit originalKit, Player player) {
        this.kitName = kitName;
        this.level = level;
        this.unlocked = unlocked;
        this.hasLevel = hasLevel;
        this.canAfford = canAfford;
        this.tieredKit = tieredKit;
        this.kitLevel = kitLevel;
        this.originalKit = originalKit;
        this.player = player;
    }

    public KitDisplayInfo(String name, int i, boolean b, boolean b1, boolean b2, Kit defaultKit, Player player) {
        this(name, i, b, b1, b2, null, null, defaultKit, player);
    }

    public KitDisplayInfo(String baseName, int level, boolean unlocked, boolean hasLevel, boolean canAfford,
            TieredKit tieredKit, KitLevel levelInfo, Player player) {
        this(baseName, level, unlocked, hasLevel, canAfford, tieredKit, levelInfo, null, player);
    }
}