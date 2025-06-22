package com.cookiebuild.microbattles.kits;

public class KitLevel {
    private final int level;
    private final int price;
    private final int requiredLevel;
    private final boolean defaultUnlocked;

    public KitLevel(int level, int price, int requiredLevel, boolean defaultUnlocked) {
        this.level = level;
        this.price = price;
        this.requiredLevel = requiredLevel;
        this.defaultUnlocked = defaultUnlocked;
    }

    public int getLevel() {
        return level;
    }

    public int getPrice() {
        return price;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public boolean isDefaultUnlocked() {
        return defaultUnlocked;
    }

    public String getDisplayName(String baseName) {
        return baseName + " " + getRomanNumeral(level);
    }

    private String getRomanNumeral(int number) {
        switch (number) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            default:
                return String.valueOf(number);
        }
    }
}