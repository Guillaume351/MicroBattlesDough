package com.cookiebuild.microbattles.map;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/** Removes legacy block-entity records whose block no longer supports one. */
public final class BlockEntitySanitizer {
    private BlockEntitySanitizer() {
    }

    public static int sanitize(Chunk chunk) {
        List<Block> candidates = new ArrayList<>();
        // This overload exposes each raw block-entity position to the predicate before
        // CraftBukkit tries to create a BlockState. Returning false avoids triggering the
        // validation exception that this sanitizer is intended to repair.
        chunk.getTileEntities(block -> {
            candidates.add(block);
            return false;
        }, false);

        int removed = 0;
        for (Block block : candidates) {
            if (hasValidTileState(block)) {
                continue;
            }

            BlockData originalData = block.getBlockData();
            Material originalMaterial = originalData.getMaterial();
            boolean isAir = originalMaterial == Material.AIR || originalMaterial == Material.CAVE_AIR
                    || originalMaterial == Material.VOID_AIR;
            Material replacement = isAir ? Material.BARRIER : Material.AIR;

            // Replacing the block makes Paper discard the orphaned block entity. Restore the
            // exact block data immediately, before the chunk can be sent to a player.
            block.setType(replacement, false);
            block.setBlockData(originalData, false);
            removed++;
        }
        return removed;
    }

    private static boolean hasValidTileState(Block block) {
        try {
            // A snapshot round-trips the block entity through Paper's validator. Legacy
            // entries such as a Sign NBT record attached to bedrock fail here.
            return block.getState(true) instanceof TileState;
        } catch (RuntimeException exception) {
            if (isInvalidBlockEntityFailure(exception)) {
                return false;
            }
            throw exception;
        }
    }

    private static boolean isInvalidBlockEntityFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().startsWith("Invalid block entity ")) {
                return true;
            }
        }
        return false;
    }
}
