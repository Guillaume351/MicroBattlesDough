package com.cookiebuild.microbattles.kits;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Coalesces repeated kit mutations and enforces a short post-write cooldown. */
final class PlayerActionGate {
    private static final int MAX_TRACKED_PLAYERS = 4_096;
    private final long cooldownMillis;
    private final LongSupplier clock;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    PlayerActionGate(long cooldownMillis, LongSupplier clock) {
        if (cooldownMillis < 0) throw new IllegalArgumentException("cooldownMillis cannot be negative");
        this.cooldownMillis = cooldownMillis;
        this.clock = clock;
    }

    boolean tryBegin(UUID playerId) {
        long now = clock.getAsLong();
        return cooldowns.getOrDefault(playerId, 0L) <= now && inFlight.add(playerId);
    }

    void finish(UUID playerId) {
        inFlight.remove(playerId);
        if (cooldowns.size() >= MAX_TRACKED_PLAYERS && !cooldowns.containsKey(playerId)) {
            cooldowns.keySet().stream().findFirst().ifPresent(cooldowns::remove);
        }
        cooldowns.put(playerId, clock.getAsLong() + cooldownMillis);
    }

    void clear(UUID playerId) {
        inFlight.remove(playerId);
        cooldowns.remove(playerId);
    }
}
