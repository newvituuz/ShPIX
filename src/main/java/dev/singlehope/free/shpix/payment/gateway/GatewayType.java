package dev.singlehope.free.shpix.payment.gateway;

import java.util.Locale;

public enum GatewayType {

    MERCADO_PAGO("Mercado Pago");

    private final String displayName;

    GatewayType(final String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return this.displayName;
    }

    public static GatewayType parse(final String raw) {
        if (raw == null) {
            return null;
        }
        final String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (final GatewayType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
