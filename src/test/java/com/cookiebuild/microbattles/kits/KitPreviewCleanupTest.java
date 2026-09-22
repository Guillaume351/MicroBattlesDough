package com.cookiebuild.microbattles.kits;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class KitPreviewCleanupTest {
    @Test
    void previewExpirationUsesTheFullLoadoutCleanup() throws IOException {
        String source = source();
        int previewStart = source.indexOf("public boolean previewKit");
        String expiration = source.substring(source.indexOf("runTaskLater", previewStart),
                source.indexOf("}, 160L)", previewStart));
        String cleanup = source.substring(source.indexOf("static void clearKitLoadout"),
                source.indexOf("private void equipExplosiveArcher"));

        assertTrue(expiration.contains("clearKitLoadout(player)"));
        assertTrue(cleanup.contains("getActivePotionEffects()"));
        assertTrue(cleanup.contains("removePotionEffect(effect.getType())"));
    }

    @Test
    void fullLoadoutCleanupClearsPreviewEquipment() {
        AtomicBoolean inventoryCleared = new AtomicBoolean();
        AtomicBoolean armorCleared = new AtomicBoolean();
        AtomicBoolean offHandCleared = new AtomicBoolean();

        PlayerInventory inventory = proxy(PlayerInventory.class, (method, arguments) -> {
            switch (method.getName()) {
                case "clear" -> inventoryCleared.set(true);
                case "setArmorContents" -> armorCleared.set(arguments != null && arguments[0] == null);
                case "setItemInOffHand" -> offHandCleared.set(arguments != null && arguments[0] == null);
                default -> { }
            }
            return defaultValue(method.getReturnType());
        });
        Player player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
            case "getInventory" -> inventory;
            case "getActivePotionEffects" -> Set.of();
            default -> defaultValue(method.getReturnType());
        });

        KitManager.clearKitLoadout(player);

        assertTrue(inventoryCleared.get());
        assertTrue(armorCleared.get());
        assertTrue(offHandCleared.get());
    }

    private static String source() throws IOException {
        Path base = Path.of("src/main/java/com/cookiebuild/microbattles/kits/KitManager.java");
        if (!Files.exists(base)) base = Path.of("MicroBattles").resolve(base);
        return Files.readString(base);
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, arguments) -> invocation.invoke(method, arguments)));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        return 0.0d;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }
}
