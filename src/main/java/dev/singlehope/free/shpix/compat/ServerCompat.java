package dev.singlehope.free.shpix.compat;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerCompat {

    private static final Pattern VERSION = Pattern.compile("(?i)\\(MC: (\\d+)\\.(\\d+)(?:\\.(\\d+))?\\)");

    private static final int MINOR = detectMinor();
    private static final String NMS_PACKAGE = detectNmsPackage();
    private static final boolean FOLIA = hasClass("io.papermc.paper.threadedregions.RegionizedServer");
    private static final boolean PERSISTENT_DATA = hasClass("org.bukkit.persistence.PersistentDataContainer");
    private static final boolean PAPER_PROFILE = hasClass("com.destroystokyo.paper.profile.PlayerProfile");

    private ServerCompat() {
    }

    private static int detectMinor() {
        final Matcher matcher = VERSION.matcher(Bukkit.getVersion());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
                // cai no fallback abaixo
            }
        }
        final String name = Bukkit.getBukkitVersion();
        final int dash = name.indexOf('-');
        final String raw = dash > 0 ? name.substring(0, dash) : name;
        final String[] parts = raw.split("\\.");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                // assume moderno
            }
        }
        return 20;
    }

    private static String detectNmsPackage() {
        final String name = Bukkit.getServer().getClass().getPackage().getName();
        final int index = name.lastIndexOf('.');
        final String suffix = index < 0 ? "" : name.substring(index + 1);
        return suffix.startsWith("v") ? suffix : "";
    }

    private static boolean hasClass(final String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    public static int minorVersion() {
        return MINOR;
    }

    public static boolean atLeast(final int minor) {
        return MINOR >= minor;
    }

    public static boolean isLegacy() {
        return MINOR < 13;
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static boolean hasPersistentData() {
        return PERSISTENT_DATA;
    }

    public static boolean hasPaperProfile() {
        return PAPER_PROFILE;
    }

    public static String nmsPackage() {
        return NMS_PACKAGE;
    }

    public static Class<?> nmsClass(final String legacyName, final String modernName) throws ClassNotFoundException {
        if (!NMS_PACKAGE.isEmpty()) {
            return Class.forName("net.minecraft.server." + NMS_PACKAGE + "." + legacyName);
        }
        return Class.forName(modernName);
    }

    public static Class<?> craftClass(final String path) throws ClassNotFoundException {
        final String base = Bukkit.getServer().getClass().getPackage().getName();
        return Class.forName(base + "." + path);
    }

    public static String describe() {
        return "MC 1." + MINOR + (FOLIA ? " (Folia)" : "") + (NMS_PACKAGE.isEmpty() ? "" : " [" + NMS_PACKAGE + "]");
    }
}
