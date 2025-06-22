package com.cookiebuild.microbattles.kits;

import java.util.ArrayList;
import java.util.List;

public class TieredKit {
    private final String baseName;
    private final List<KitLevel> levels;

    public TieredKit(String baseName) {
        this.baseName = baseName;
        this.levels = new ArrayList<>();
    }

    public void addLevel(int level, int price, int requiredLevel, boolean defaultUnlocked) {
        levels.add(new KitLevel(level, price, requiredLevel, defaultUnlocked));
    }

    public String getBaseName() {
        return baseName;
    }

    public List<KitLevel> getLevels() {
        return levels;
    }

    public KitLevel getLevel(int level) {
        return levels.stream().filter(l -> l.getLevel() == level).findFirst().orElse(null);
    }
}