package com.cookiebuild.microbattles.listener;

import com.cookiebuild.microbattles.MicroBattles;
import com.cookiebuild.microbattles.map.BlockEntitySanitizer;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Locale;

public final class MatchChunkIntegrityListener implements Listener {
    private static final String MATCH_WORLD_PREFIX = "match_";

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        NamespacedKey worldKey = event.getWorld().getKey();
        if (!worldKey.getNamespace().equals(MicroBattles.getInstance().getName().toLowerCase(Locale.ROOT))
                || !worldKey.getKey().startsWith(MATCH_WORLD_PREFIX)) {
            return;
        }

        int removed = BlockEntitySanitizer.sanitize(event.getChunk());
        if (removed > 0) {
            MicroBattles.getInstance().getLogger().warning("Removed " + removed
                    + " orphaned block entities from " + worldKey + " chunk "
                    + event.getChunk().getX() + "," + event.getChunk().getZ());
        }
    }
}
