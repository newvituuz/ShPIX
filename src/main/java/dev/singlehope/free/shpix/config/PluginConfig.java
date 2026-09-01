package dev.singlehope.free.shpix.config;

import dev.singlehope.free.shpix.util.Money;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class PluginConfig {

    private static final BigDecimal DEFAULT_MIN_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal DEFAULT_MAX_AMOUNT = new BigDecimal("5000.00");
    private static final BigDecimal DEFAULT_FEE_PERCENT = new BigDecimal("0.99");
    private static final BigDecimal DEFAULT_MAX_COUPON = new BigDecimal("90.00");

    private final List<String> warnings = new ArrayList<>();

    private final String databaseHost;
    private final int databasePort;
    private final String databaseName;
    private final String databaseUser;
    private final String databasePassword;
    private final String databaseProperties;
    private final int databasePoolSize;
    private final String tablePrefix;

    private final Duration orderExpiration;
    private final Duration pollInterval;
    private final Duration httpTimeout;
    private final int requestsPerMinute;
    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;
    private final BigDecimal feePercent;
    private final BigDecimal maxCouponPercent;
    private final boolean requireEmptyInventoryForItems;
    private final String payerEmailDomain;

    private final boolean discordEnabled;
    private final String discordWebhookUrl;
    private final String discordTitle;
    private final String discordDescription;
    private final String discordFooter;
    private final String discordFieldPlayer;
    private final String discordFieldPrice;
    private final String discordFieldFee;
    private final int discordColor;

    private PluginConfig(final FileConfiguration config) {
        this.databaseHost = string(config, "database.host", "127.0.0.1");
        this.databasePort = clampInt(config, "database.port", 3306, 1, 65535);
        this.databaseName = string(config, "database.database", "shpix");
        this.databaseUser = string(config, "database.username", "root");
        this.databasePassword = config.getString("database.password", "");
        this.databaseProperties = string(config, "database.properties",
                "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC");
        this.databasePoolSize = clampInt(config, "database.pool-size", 8, 1, 32);
        this.tablePrefix = sanitizePrefix(string(config, "database.table-prefix", "shpix_"));

        this.orderExpiration = Duration.ofMinutes(clampInt(config, "payment.expiration-minutes", 30, 1, 720));
        this.pollInterval = Duration.ofSeconds(clampInt(config, "payment.poll-interval-seconds", 20, 5, 600));
        this.httpTimeout = Duration.ofSeconds(clampInt(config, "payment.request-timeout-seconds", 15, 3, 60));
        this.requestsPerMinute = clampInt(config, "payment.max-requests-per-minute", 240, 10, 3000);
        this.requireEmptyInventoryForItems = config.getBoolean("payment.require-empty-inventory-for-items", true);
        this.payerEmailDomain = sanitizeDomain(string(config, "payment.payer-email-domain", "shpix.local"));

        BigDecimal min = Money.fromConfig(config.get("payment.min-amount"), DEFAULT_MIN_AMOUNT);
        BigDecimal max = Money.fromConfig(config.get("payment.max-amount"), DEFAULT_MAX_AMOUNT);
        if (!Money.isPositive(min)) {
            this.warnings.add("payment.min-amount inválido; usando " + DEFAULT_MIN_AMOUNT.toPlainString() + ".");
            min = DEFAULT_MIN_AMOUNT;
        }
        if (!Money.isPositive(max) || max.compareTo(min) < 0) {
            this.warnings.add("payment.max-amount inválido; usando " + DEFAULT_MAX_AMOUNT.toPlainString() + ".");
            max = DEFAULT_MAX_AMOUNT.max(min);
        }
        this.minAmount = min;
        this.maxAmount = max;

        BigDecimal fee = Money.fromConfig(config.get("payment.fee-percent"), DEFAULT_FEE_PERCENT);
        if (fee == null || fee.signum() < 0 || fee.compareTo(BigDecimal.valueOf(100)) >= 0) {
            this.warnings.add("payment.fee-percent inválido; usando " + DEFAULT_FEE_PERCENT.toPlainString() + "%.");
            fee = DEFAULT_FEE_PERCENT;
        }
        this.feePercent = fee;

        BigDecimal maxCoupon = Money.fromConfig(config.get("coupons.max-percent"), DEFAULT_MAX_COUPON);
        if (maxCoupon == null || maxCoupon.signum() <= 0 || maxCoupon.compareTo(BigDecimal.valueOf(100)) >= 0) {
            this.warnings.add("coupons.max-percent inválido; usando " + DEFAULT_MAX_COUPON.toPlainString() + "%.");
            maxCoupon = DEFAULT_MAX_COUPON;
        }
        this.maxCouponPercent = maxCoupon;

        final String webhook = config.getString("discord.webhook-url", "").trim();
        final boolean webhookValid = webhook.startsWith("https://discord.com/api/webhooks/")
                || webhook.startsWith("https://discordapp.com/api/webhooks/")
                || webhook.startsWith("https://ptb.discord.com/api/webhooks/")
                || webhook.startsWith("https://canary.discord.com/api/webhooks/");
        final boolean requested = config.getBoolean("discord.enabled", false);
        if (requested && !webhookValid) {
            this.warnings.add("discord.enabled está ativo mas discord.webhook-url não é um webhook válido; notificações desativadas.");
        }
        this.discordEnabled = requested && webhookValid;
        this.discordWebhookUrl = webhookValid ? webhook : "";
        this.discordTitle = string(config, "discord.embed.title", "Uma nova venda foi encontrada!");
        this.discordDescription = string(config, "discord.embed.description", "Uma nova venda do produto {product} foi encontrada!");
        this.discordFooter = string(config, "discord.embed.footer", "Adquirido por {player} em {date}");
        this.discordFieldPlayer = string(config, "discord.embed.fields.player", "Comprador");
        this.discordFieldPrice = string(config, "discord.embed.fields.price", "Custo");
        this.discordFieldFee = string(config, "discord.embed.fields.fee", "Taxa");
        this.discordColor = parseColor(string(config, "discord.embed.color", "#2ECC71"));
    }

    public static PluginConfig load(final FileConfiguration config, final Logger logger) {
        final PluginConfig loaded = new PluginConfig(config);
        for (final String warning : loaded.warnings) {
            logger.warning(warning);
        }
        return loaded;
    }

    private static String string(final FileConfiguration config, final String path, final String fallback) {
        final String value = config.getString(path);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int clampInt(final FileConfiguration config, final String path, final int fallback,
                                final int min, final int max) {
        final int value = config.getInt(path, fallback);
        return Math.max(min, Math.min(max, value));
    }

    private static String sanitizePrefix(final String raw) {
        final String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return cleaned.isEmpty() ? "shpix_" : cleaned;
    }

    private static String sanitizeDomain(final String raw) {
        final String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]", "");
        return cleaned.isEmpty() || !cleaned.contains(".") ? "shpix.local" : cleaned;
    }

    private static int parseColor(final String raw) {
        final String value = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return 0x2ECC71;
        }
    }

    public String gatewayToken(final FileConfiguration config, final String gatewayName) {
        final ConfigurationSection section = config.getConfigurationSection("gateways." + gatewayName);
        if (section == null) {
            return "";
        }
        final String token = section.getString("access-token", section.getString("authentication", ""));
        return token == null ? "" : token.trim();
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + this.databaseHost + ":" + this.databasePort + "/" + this.databaseName
                + "?" + this.databaseProperties;
    }

    public String databaseUser() {
        return this.databaseUser;
    }

    public String databasePassword() {
        return this.databasePassword;
    }

    public int databasePoolSize() {
        return this.databasePoolSize;
    }

    public String tablePrefix() {
        return this.tablePrefix;
    }

    public Duration orderExpiration() {
        return this.orderExpiration;
    }

    public Duration pollInterval() {
        return this.pollInterval;
    }

    public Duration httpTimeout() {
        return this.httpTimeout;
    }

    public int requestsPerMinute() {
        return this.requestsPerMinute;
    }

    public BigDecimal minAmount() {
        return this.minAmount;
    }

    public BigDecimal maxAmount() {
        return this.maxAmount;
    }

    public BigDecimal feePercent() {
        return this.feePercent;
    }

    public BigDecimal maxCouponPercent() {
        return this.maxCouponPercent;
    }

    public boolean requireEmptyInventoryForItems() {
        return this.requireEmptyInventoryForItems;
    }

    public String payerEmailDomain() {
        return this.payerEmailDomain;
    }

    public boolean discordEnabled() {
        return this.discordEnabled;
    }

    public String discordWebhookUrl() {
        return this.discordWebhookUrl;
    }

    public String discordTitle() {
        return this.discordTitle;
    }

    public String discordDescription() {
        return this.discordDescription;
    }

    public String discordFooter() {
        return this.discordFooter;
    }

    public String discordFieldPlayer() {
        return this.discordFieldPlayer;
    }

    public String discordFieldPrice() {
        return this.discordFieldPrice;
    }

    public String discordFieldFee() {
        return this.discordFieldFee;
    }

    public int discordColor() {
        return this.discordColor;
    }
}
