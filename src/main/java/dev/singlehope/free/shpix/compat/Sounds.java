package dev.singlehope.free.shpix.compat;

import org.bukkit.Sound;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class Sounds {

    private static final Map<String, String[]> ALIASES = new LinkedHashMap<>();
    private static final Map<String, Optional<Sound>> CACHE = new ConcurrentHashMap<>();

    static {
        alias("ENTITY_PLAYER_LEVELUP", "LEVEL_UP");
        alias("ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP");
        alias("BLOCK_ANVIL_LAND", "ANVIL_LAND");
        alias("BLOCK_ANVIL_BREAK", "ANVIL_BREAK");
        alias("BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_PLING", "NOTE_PLING");
        alias("UI_BUTTON_CLICK", "CLICK");
        alias("ENTITY_VILLAGER_NO", "VILLAGER_NO");
        alias("ENTITY_VILLAGER_YES", "VILLAGER_YES");
    }

    private Sounds() {
    }

    private static void alias(final String modern, final String... legacy) {
        final String[] all = new String[legacy.length + 1];
        all[0] = modern;
        System.arraycopy(legacy, 0, all, 1, legacy.length);
        ALIASES.put(modern, all);
        for (final String name : legacy) {
            ALIASES.put(name, all);
        }
    }

    public static Optional<Sound> resolve(final String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return CACHE.computeIfAbsent(name.trim().toUpperCase(Locale.ROOT), Sounds::lookup);
    }

    private static Optional<Sound> lookup(final String name) {
        for (final String candidate : ALIASES.getOrDefault(name, new String[]{name})) {
            try {
                return Optional.of(Sound.valueOf(candidate));
            } catch (IllegalArgumentException ignored) {
                // tenta o próximo nome conhecido
            }
        }
        return Optional.empty();
    }

    public static Sound defaultSound() {
        return resolve("ENTITY_PLAYER_LEVELUP").orElseGet(() -> Sound.values()[0]);
    }
}
