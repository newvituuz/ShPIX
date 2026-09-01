package dev.singlehope.free.shpix.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private static final Locale BRAZIL = Locale.forLanguageTag("pt-BR");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_SUPPORTED = new BigDecimal("999999999.99");

    private Money() {
    }

    public static BigDecimal normalize(final BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(final String raw) {
        if (raw == null) {
            return null;
        }
        final String cleaned = raw.trim().replace(" ", "").replace("R$", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        final String normalized = cleaned.indexOf(',') >= 0 && cleaned.indexOf('.') >= 0
                ? cleaned.replace(".", "").replace(',', '.')
                : cleaned.replace(',', '.');
        try {
            final BigDecimal parsed = new BigDecimal(normalized);
            if (parsed.abs().compareTo(MAX_SUPPORTED) > 0) {
                return null;
            }
            return normalize(parsed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static BigDecimal fromConfig(final Object raw, final BigDecimal fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof BigDecimal decimal) {
            return normalize(decimal);
        }
        if (raw instanceof Number number) {
            final BigDecimal parsed = new BigDecimal(number.toString());
            return parsed.abs().compareTo(MAX_SUPPORTED) > 0 ? fallback : normalize(parsed);
        }
        final BigDecimal parsed = of(raw.toString());
        return parsed == null ? fallback : parsed;
    }

    public static BigDecimal percentOf(final BigDecimal base, final BigDecimal percent) {
        if (base == null || percent == null) {
            return ZERO;
        }
        return normalize(base.multiply(percent).divide(HUNDRED, SCALE + 4, ROUNDING));
    }

    public static boolean isPositive(final BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isWithin(final BigDecimal value, final BigDecimal min, final BigDecimal max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    public static String format(final BigDecimal value) {
        final DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(BRAZIL));
        return format.format(normalize(value));
    }

    public static String toPlainString(final BigDecimal value) {
        return normalize(value).toPlainString();
    }
}
