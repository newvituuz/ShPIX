package dev.singlehope.free.shpix.payment;

import java.util.Locale;

public enum OrderStatus {

    WAITING,
    PAID,
    DELIVERED,
    EXPIRED,
    CANCELLED,
    REFUNDED;

    public static OrderStatus parse(final String raw) {
        if (raw == null) {
            return CANCELLED;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CANCELLED;
        }
    }
}
