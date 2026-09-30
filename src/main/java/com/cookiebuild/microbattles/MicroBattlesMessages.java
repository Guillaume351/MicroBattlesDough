package com.cookiebuild.microbattles;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import com.cookiebuild.cookiedough.utils.LocaleManager;

/**
 * MicroBattles-owned translations, resolved through this plugin's class loader
 * (the shared CookieDough loader cannot see module resources on Paper). Keys
 * missing here fall back to the shared CookieDough {@code messages} bundle.
 */
public final class MicroBattlesMessages {
    public static final String BUNDLE = "microbattles_messages";
    public static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.ENGLISH, Locale.FRENCH, Locale.of("es"), Locale.of("pt", "BR"),
            Locale.of("bg"), Locale.of("hi"), Locale.GERMAN, Locale.ITALIAN);
    private static final ResourceBundle.Control NO_SYSTEM_LOCALE_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private MicroBattlesMessages() {
    }

    public static String get(String key, Locale locale, Object... arguments) {
        String pattern = lookup(key, locale);
        if (pattern == null) {
            return LocaleManager.getMessage(key, locale, arguments);
        }
        for (int index = 0; index < arguments.length; index++) {
            pattern = pattern.replace("{" + index + "}", String.valueOf(arguments[index]));
        }
        return pattern;
    }

    static String lookup(String key, Locale locale) {
        for (Locale candidate : candidates(locale == null ? Locale.ENGLISH : locale)) {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, candidate,
                        MicroBattlesMessages.class.getClassLoader(), NO_SYSTEM_LOCALE_FALLBACK);
                if (bundle.containsKey(key)) {
                    return bundle.getString(key);
                }
            } catch (MissingResourceException ignored) {
                // Try the next, less specific candidate.
            }
        }
        return null;
    }

    private static Set<Locale> candidates(Locale locale) {
        Set<Locale> candidates = new LinkedHashSet<>();
        candidates.add(locale);
        if ("pt".equals(locale.getLanguage())) {
            candidates.add(Locale.of("pt", "BR"));
        }
        candidates.add(Locale.of(locale.getLanguage()));
        candidates.add(Locale.ENGLISH);
        return candidates;
    }
}
