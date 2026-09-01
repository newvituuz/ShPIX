package dev.singlehope.free.shpix.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Text {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private Text() {
    }

    public static Component color(final String raw) {
        return AMPERSAND.deserialize(raw == null ? "" : raw.replace('§', '&'));
    }

    public static Component item(final String raw) {
        return color(raw).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> itemLore(final List<String> raw) {
        final List<Component> lore = new ArrayList<>();
        if (raw == null) {
            return lore;
        }
        for (final String line : raw) {
            if (line == null) {
                continue;
            }
            for (final String part : line.split("\n", -1)) {
                lore.add(item(part));
            }
        }
        return lore;
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
