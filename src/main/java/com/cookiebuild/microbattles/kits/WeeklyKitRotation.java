package com.cookiebuild.microbattles.kits;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Deterministic two-kit weekly rotation, shared by unlock checks and both UIs. */
public final class WeeklyKitRotation {
    private static final int ROTATION_SIZE = 2;

    private WeeklyKitRotation() {
    }

    public static List<String> forDate(Collection<String> kitNames, LocalDate date) {
        List<String> candidates = kitNames.stream()
                .filter(name -> !"Default".equalsIgnoreCase(name))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        WeekFields iso = WeekFields.ISO;
        long seed = date.get(iso.weekBasedYear()) * 100L + date.get(iso.weekOfWeekBasedYear());
        Collections.shuffle(candidates, new Random(seed));
        return List.copyOf(candidates.subList(0, Math.min(ROTATION_SIZE, candidates.size())));
    }

    public static boolean contains(Collection<String> kitNames, LocalDate date, String kitName) {
        return forDate(kitNames, date).stream().anyMatch(name -> name.equalsIgnoreCase(kitName));
    }
}
