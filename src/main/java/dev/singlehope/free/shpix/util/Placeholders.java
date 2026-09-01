package dev.singlehope.free.shpix.util;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class Placeholders {

    private static volatile boolean available;

    private Placeholders() {
    }

    public static void detect() {
        available = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static String apply(final Player player, final String input) {
        if (!available || input == null) {
            return input;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, input);
        } catch (Exception ignored) {
            return input;
        }
    }

    public static List<String> apply(final Player player, final List<String> input) {
        if (!available || input == null) {
            return input;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, input);
        } catch (Exception ignored) {
            return input;
        }
    }
}
