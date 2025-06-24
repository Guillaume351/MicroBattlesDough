package com.cookiebuild.microbattles.ui.bedrock;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

public class BedrockUIHelper {

    private static boolean floodgateApiAvailable = false;

    static {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApiAvailable = true;
        } catch (ClassNotFoundException e) {
            floodgateApiAvailable = false;
        }
    }

    public static boolean isFloodgateApiAvailable() {
        return floodgateApiAvailable;
    }

    public static boolean isBedrockPlayer(Player player) {
        if (!isFloodgateApiAvailable()) {
            return false;
        }
        return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }
}