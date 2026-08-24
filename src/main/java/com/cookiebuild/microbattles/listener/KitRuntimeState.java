package com.cookiebuild.microbattles.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Match-scoped kit timers whose wall-clock expiry survives a transport reconnect. */
final class KitRuntimeState {
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> assassinBonusExpiresAt = new HashMap<>();
    private final Map<UUID, Long> lastSneakTime = new HashMap<>();

    long useCooldown(UUID playerId, String ability, long cooldownMillis, long now) {
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(playerId, ignored -> new HashMap<>());
        long lastUsed = playerCooldowns.getOrDefault(ability, Long.MIN_VALUE);
        if (lastUsed != Long.MIN_VALUE && now - lastUsed < cooldownMillis) {
            return Math.max(1L, (cooldownMillis - (now - lastUsed) + 999L) / 1_000L);
        }
        playerCooldowns.put(ability, now);
        return 0L;
    }

    boolean recordSneak(UUID playerId, long now) {
        Long previous = lastSneakTime.put(playerId, now);
        if (previous == null || now - previous >= 500L) return false;
        lastSneakTime.remove(playerId);
        return true;
    }

    void armAssassinBonus(UUID playerId, long expiresAtMillis) {
        assassinBonusExpiresAt.put(playerId, expiresAtMillis);
    }

    boolean consumeAssassinBonus(UUID playerId, long now) {
        Long expiresAt = assassinBonusExpiresAt.remove(playerId);
        return expiresAt != null && expiresAt > now;
    }

    void clear(UUID playerId) {
        cooldowns.remove(playerId);
        assassinBonusExpiresAt.remove(playerId);
        lastSneakTime.remove(playerId);
    }
}
