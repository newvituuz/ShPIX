package dev.singlehope.free.shpix.util;

import dev.singlehope.free.shpix.compat.Chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Text {

    private Text() {
    }

    public static String color(final String raw) {
        return Chat.color(raw);
    }

    public static List<String> colorAll(final List<String> raw) {
        final List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (final String line : raw) {
            if (line == null) {
                continue;
            }
            for (final String part : line.replace("{nl}", "\n").split("\n", -1)) {
                result.add(Chat.color(part));
            }
        }
        return result;
    }

    public static String apply(final String raw, final Map<String, String> placeholders) {
        if (raw == null) {
            return "";
        }
        String result = raw;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    public static List<String> apply(final List<String> raw, final Map<String, String> placeholders) {
        final List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (final String line : raw) {
            result.add(apply(line, placeholders));
        }
        return result;
    }
}
