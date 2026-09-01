package dev.singlehope.free.shpix.coupon;

import dev.singlehope.free.shpix.config.PluginConfig;
import dev.singlehope.free.shpix.util.Money;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class CouponService {

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");

    private final Plugin plugin;
    private final File file;
    private final Map<String, Coupon> coupons = new ConcurrentHashMap<>();

    private volatile PluginConfig config;

    public CouponService(final Plugin plugin, final PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "coupons.yml");
        migrateLegacyFile();
        reload(config);
    }

    private void migrateLegacyFile() {
        final File legacy = new File(this.plugin.getDataFolder(), "descontos.yml");
        if (legacy.exists() && !this.file.exists() && !legacy.renameTo(this.file)) {
            this.plugin.getLogger().warning("Não foi possível migrar descontos.yml para coupons.yml.");
        }
    }

    public void reload(final PluginConfig newConfig) {
        this.config = newConfig;
        this.coupons.clear();
        if (!this.file.exists()) {
            return;
        }
        final YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(this.file);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("coupons.yml inválido; nenhum cupom carregado.");
            return;
        }
        final ConfigurationSection section = yaml.getConfigurationSection("coupons");
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final BigDecimal percent = Money.fromConfig(section.get(key + ".percent", section.get(key + ".percentage")), null);
            if (percent == null || !isValidPercent(percent)) {
                this.plugin.getLogger().warning("Cupom '" + key + "' ignorado: porcentagem inválida.");
                continue;
            }
            this.coupons.put(key.toLowerCase(Locale.ROOT), new Coupon(key, percent));
        }
    }

    public boolean isValidName(final String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public boolean isValidPercent(final BigDecimal percent) {
        return percent != null
                && percent.compareTo(BigDecimal.ZERO) > 0
                && percent.compareTo(this.config.maxCouponPercent()) <= 0;
    }

    public BigDecimal maxPercent() {
        return this.config.maxCouponPercent();
    }

    public Optional<Coupon> find(final String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.coupons.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    public List<Coupon> all() {
        return new ArrayList<>(this.coupons.values());
    }

    public synchronized boolean save(final Coupon coupon) {
        if (!isValidName(coupon.name()) || !isValidPercent(coupon.percent())) {
            return false;
        }
        this.coupons.put(coupon.name().toLowerCase(Locale.ROOT), coupon);
        return persist();
    }

    public synchronized boolean remove(final String name) {
        if (name == null || this.coupons.remove(name.toLowerCase(Locale.ROOT)) == null) {
            return false;
        }
        return persist();
    }

    private boolean persist() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Coupon coupon : this.coupons.values()) {
            yaml.set("coupons." + coupon.name() + ".percent", coupon.percent().toPlainString());
        }
        try {
            yaml.save(this.file);
            return true;
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Não foi possível salvar coupons.yml.");
            return false;
        }
    }
}
