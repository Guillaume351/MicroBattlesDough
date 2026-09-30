package com.cookiebuild.microbattles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class MicroBattlesMessagesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    @Test
    void everySupportedLocaleDefinesEveryKeyWithMatchingPlaceholders() {
        ResourceBundle english = bundle(Locale.ENGLISH);
        for (Locale locale : MicroBattlesMessages.SUPPORTED_LOCALES) {
            ResourceBundle localized = bundle(locale);
            assertEquals(english.keySet(), localized.keySet(), "keys for " + locale);
            for (String key : english.keySet()) {
                assertFalse(localized.getString(key).isBlank(), key + " for " + locale);
                assertEquals(placeholders(english.getString(key)), placeholders(localized.getString(key)),
                        key + " placeholders for " + locale);
            }
        }
    }

    @Test
    void resolvesRegionalVariantsAndFallsBackToEnglish() {
        assertEquals("Carte 3", MicroBattlesMessages.get("microbattles.map.numbered", Locale.CANADA_FRENCH, 3));
        assertEquals("Mapa 2", MicroBattlesMessages.get("microbattles.map.numbered", Locale.of("pt", "PT"), 2));
        assertEquals("Map 7", MicroBattlesMessages.get("microbattles.map.numbered", Locale.JAPANESE, 7));
        assertEquals("Map 1", MicroBattlesMessages.get("microbattles.map.numbered", null, 1));
    }

    @Test
    void frenchVoteAnnouncementIsPlainTextWithTheCommand() {
        assertEquals("Vote pour la prochaine carte : /mbvote",
                MicroBattlesMessages.get("microbattles.map_vote.announce", Locale.FRENCH));
    }

    private static ResourceBundle bundle(Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle(MicroBattlesMessages.BUNDLE, locale,
                MicroBattlesMessagesTest.class.getClassLoader(), NO_FALLBACK);
        assertNotNull(bundle);
        assertEquals(locale, bundle.getLocale(), "dedicated bundle for " + locale);
        return bundle;
    }

    private static Set<String> placeholders(String value) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) found.add(matcher.group());
        return found;
    }
}
