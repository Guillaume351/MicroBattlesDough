package com.cookiebuild.microbattles;

import org.bukkit.plugin.java.JavaPlugin;

public final class MicroBattles extends JavaPlugin {
    private static MicroBattles instance;

    public static MicroBattles getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        this.getLogger().info("Enabling MicroBattles...");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
