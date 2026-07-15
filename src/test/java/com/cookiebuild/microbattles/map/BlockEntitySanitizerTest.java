package com.cookiebuild.microbattles.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

class BlockEntitySanitizerTest {
    @Test
    void removesOrphanedBlockEntityAndRestoresExactBlockData() {
        BlockData bedrock = proxy(BlockData.class, (method, args) -> {
            if (method.getName().equals("getMaterial")) {
                return Material.BEDROCK;
            }
            return defaultValue(method.getReturnType());
        });
        AtomicReference<Material> replacement = new AtomicReference<>();
        AtomicReference<BlockData> restored = new AtomicReference<>();
        BlockState validState = proxy(TileState.class,
                (method, args) -> defaultValue(method.getReturnType()));
        Block block = proxy(Block.class, (method, args) -> switch (method.getName()) {
            case "getState" -> throw new RuntimeException("Failed to read block state",
                    new IllegalStateException(
                            "Invalid block entity minecraft:sign at BlockPos{-2,39,1}, got Block{minecraft:bedrock}"));
            case "getBlockData" -> bedrock;
            case "setType" -> {
                replacement.set((Material) args[0]);
                yield null;
            }
            case "setBlockData" -> {
                restored.set((BlockData) args[0]);
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        AtomicInteger validStateReads = new AtomicInteger();
        Block validBlock = proxy(Block.class, (method, args) -> {
            if (method.getName().equals("getState")) {
                validStateReads.incrementAndGet();
                return validState;
            }
            return defaultValue(method.getReturnType());
        });
        Chunk chunk = chunkWithRawBlockEntities(block, validBlock);

        assertEquals(1, BlockEntitySanitizer.sanitize(chunk));
        assertEquals(Material.AIR, replacement.get());
        assertSame(bedrock, restored.get());
        assertEquals(1, validStateReads.get());
    }

    @Test
    void usesNonAirReplacementForOrphanInAirBlock() {
        BlockData air = proxy(BlockData.class, (method, args) -> method.getName().equals("getMaterial")
                ? Material.CAVE_AIR
                : defaultValue(method.getReturnType()));
        AtomicReference<Material> replacement = new AtomicReference<>();
        BlockState plainState = proxy(BlockState.class,
                (method, args) -> defaultValue(method.getReturnType()));
        Block block = proxy(Block.class, (method, args) -> switch (method.getName()) {
            case "getState" -> plainState;
            case "getBlockData" -> air;
            case "setType" -> {
                replacement.set((Material) args[0]);
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        Chunk chunk = chunkWithRawBlockEntities(block);

        assertEquals(1, BlockEntitySanitizer.sanitize(chunk));
        assertEquals(Material.BARRIER, replacement.get());
    }

    @Test
    void doesNotMaskUnrelatedBlockStateFailure() {
        Block block = proxy(Block.class, (method, args) -> {
            if (method.getName().equals("getState")) {
                throw new IllegalStateException("Registry unavailable");
            }
            return defaultValue(method.getReturnType());
        });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BlockEntitySanitizer.sanitize(chunkWithRawBlockEntities(block)));
        assertEquals("Registry unavailable", failure.getMessage());
    }

    @SuppressWarnings("unchecked")
    private static Chunk chunkWithRawBlockEntities(Block... blocks) {
        return proxy(Chunk.class, (method, args) -> {
            if (method.getName().equals("getTileEntities") && args != null && args.length == 2) {
                Predicate<Block> predicate = (Predicate<Block>) args[0];
                for (Block block : blocks) {
                    predicate.test(block);
                }
                return List.of();
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (ignored, method, args) -> invocation.invoke(method, args));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] args);
    }
}
