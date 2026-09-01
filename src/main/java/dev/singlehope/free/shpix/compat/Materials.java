package dev.singlehope.free.shpix.compat;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Materials {

    private static final Map<String, String[]> ALIASES = new LinkedHashMap<>();
    private static final Map<String, Short> LEGACY_DATA = new LinkedHashMap<>();
    private static final Map<String, Resolved> CACHE = new ConcurrentHashMap<>();

    static {
        alias("FILLED_MAP", "MAP");
        alias("MAP", "EMPTY_MAP");
        alias("PLAYER_HEAD", "SKULL_ITEM");
        alias("CLOCK", "WATCH");
        alias("EXPERIENCE_BOTTLE", "EXP_BOTTLE");
        alias("ENCHANTING_TABLE", "ENCHANTMENT_TABLE");
        alias("CRAFTING_TABLE", "WORKBENCH");
        alias("OAK_SIGN", "SIGN");
        alias("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        alias("LIME_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        alias("GREEN_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        alias("RED_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        alias("BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        alias("WHITE_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");

        LEGACY_DATA.put("PLAYER_HEAD", (short) 3);
        LEGACY_DATA.put("WHITE_STAINED_GLASS_PANE", (short) 0);
        LEGACY_DATA.put("LIME_STAINED_GLASS_PANE", (short) 5);
        LEGACY_DATA.put("GRAY_STAINED_GLASS_PANE", (short) 7);
        LEGACY_DATA.put("GREEN_STAINED_GLASS_PANE", (short) 13);
        LEGACY_DATA.put("RED_STAINED_GLASS_PANE", (short) 14);
        LEGACY_DATA.put("BLACK_STAINED_GLASS_PANE", (short) 15);
    }

    private Materials() {
    }

    private static void alias(final String modern, final String legacy) {
        ALIASES.put(modern, new String[]{modern, legacy});
    }

    public static Resolved resolve(final String name) {
        if (name == null || name.isBlank()) {
            return new Resolved(Material.BARRIER, (short) 0);
        }
        return CACHE.computeIfAbsent(name.trim().toUpperCase(Locale.ROOT), Materials::lookup);
    }

    private static Resolved lookup(final String name) {
        final String[] candidates = ALIASES.getOrDefault(name, new String[]{name});
        for (int index = 0; index < candidates.length; index++) {
            final Material material = byName(candidates[index]);
            if (material == null) {
                continue;
            }
            final boolean usedLegacyName = index > 0;
            final short data = usedLegacyName ? LEGACY_DATA.getOrDefault(name, (short) 0) : 0;
            return new Resolved(material, data);
        }
        return new Resolved(Material.BARRIER, (short) 0);
    }

    private static Material byName(final String name) {
        Material material = Material.getMaterial(name);
        if (material == null) {
            material = matchLegacy(name);
        }
        return material != null && isItem(material) ? material : null;
    }

    private static Material matchLegacy(final String name) {
        try {
            return Material.getMaterial(name, true);
        } catch (NoSuchMethodError | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isItem(final Material material) {
        try {
            return material.isItem();
        } catch (NoSuchMethodError ignored) {
            return true;
        }
    }

    public static boolean isKnown(final String name) {
        return resolve(name).material() != Material.BARRIER
                || "BARRIER".equalsIgnoreCase(name == null ? "" : name.trim());
    }

    public record Resolved(Material material, short data) {
    }
}
