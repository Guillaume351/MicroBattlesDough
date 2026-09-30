package com.cookiebuild.microbattles.map;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Player-facing map names; configuration keys such as {@code game-3} stay the stable vote identifiers. */
public final class MapDisplayNames {
    private static final Pattern NUMBERED = Pattern.compile("(?i)(?:game|map|arena)[-_ ]?(\\d{1,6})");

    private MapDisplayNames() {
    }

    /**
     * @param configured optional {@code display-name} from the map configuration
     * @param numbered   localized label for generic numbered maps, e.g. "Carte 3"
     */
    public static String displayName(String mapName, String configured, IntFunction<String> numbered) {
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        if (mapName == null || mapName.isBlank()) {
            return "";
        }
        Matcher matcher = NUMBERED.matcher(mapName.strip());
        if (matcher.matches()) {
            return numbered.apply(Integer.parseInt(matcher.group(1)));
        }
        return Arrays.stream(mapName.strip().split("[-_\\s]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
