package com.cookiebuild.microbattles.map;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Global vote for the next arena instance. One current vote per player. */
public final class NextMapVote {
    private final Set<String> availableMaps = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<UUID, String> votes = new LinkedHashMap<>();

    public synchronized void configure(Collection<String> mapNames) {
        availableMaps.clear();
        availableMaps.addAll(mapNames);
        votes.entrySet().removeIf(entry -> !availableMaps.contains(entry.getValue()));
    }

    public synchronized boolean vote(UUID playerId, String mapName) {
        String canonical = availableMaps.stream().filter(name -> name.equalsIgnoreCase(mapName)).findFirst().orElse(null);
        if (playerId == null || canonical == null) {
            return false;
        }
        votes.put(playerId, canonical);
        return true;
    }

    public synchronized void removeVote(UUID playerId) {
        votes.remove(playerId);
    }

    public synchronized String consumeWinner() {
        String winner = tallies().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        votes.clear();
        return winner;
    }

    public synchronized Map<String, Long> tallies() {
        Map<String, Long> result = new LinkedHashMap<>();
        availableMaps.forEach(name -> result.put(name, 0L));
        votes.values().forEach(name -> result.computeIfPresent(name, (ignored, count) -> count + 1));
        return Map.copyOf(result);
    }

    public synchronized List<String> availableMaps() {
        return List.copyOf(availableMaps);
    }
}
