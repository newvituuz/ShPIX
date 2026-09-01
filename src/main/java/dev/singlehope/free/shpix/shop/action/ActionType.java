package dev.singlehope.free.shpix.shop.action;

import java.util.Locale;

public enum ActionType {

    COLLECT,
    REFUND,
    EXPIRED;

    public static ActionType parse(final String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
