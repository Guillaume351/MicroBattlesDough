package com.cookiebuild.microbattles.kits;

import java.util.Locale;

import com.cookiebuild.cookiedough.utils.LocaleManager;

/** Localized presentation for stable kit identifiers stored in player progression. */
public final class KitDisplayNames {
    private KitDisplayNames() { }

    public static String localized(String stableName, Locale locale) {
        if (stableName == null || stableName.isBlank()) return "";
        String key = switch (stableName) {
            case "Default" -> "microbattles.kit.name.default";
            case "Explosive Archer" -> "microbattles.kit.name.explosive_archer";
            case "Enderman" -> "microbattles.kit.name.enderman";
            case "Knockback Warrior" -> "microbattles.kit.name.knockback_warrior";
            case "Tank" -> "microbattles.kit.name.tank";
            case "Ninja" -> "microbattles.kit.name.ninja";
            case "Archer" -> "microbattles.kit.name.archer";
            case "Berserker" -> "microbattles.kit.name.berserker";
            case "Chemist" -> "microbattles.kit.name.chemist";
            case "Assassin" -> "microbattles.kit.name.assassin";
            case "Miner" -> "microbattles.kit.name.miner";
            case "Vampire" -> "microbattles.kit.name.vampire";
            case "Frost Mage" -> "microbattles.kit.name.frost_mage";
            case "Juggernaut" -> "microbattles.kit.name.juggernaut";
            case "Trapper" -> "microbattles.kit.name.trapper";
            case "Alchemist" -> "microbattles.kit.name.alchemist";
            case "Mobility" -> "microbattles.kit.name.mobility";
            default -> null;
        };
        return key == null ? stableName : LocaleManager.getMessage(key, locale);
    }

    public static String localizedTier(String stableName, int level, Locale locale) {
        String name = localized(stableName, locale);
        if (level <= 0) return name;
        return name + " " + roman(level);
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(level);
        };
    }
}
