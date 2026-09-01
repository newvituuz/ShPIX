package dev.singlehope.free.shpix.config;

import dev.singlehope.free.shpix.compat.Chat;
import dev.singlehope.free.shpix.util.Placeholders;
import dev.singlehope.free.shpix.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Messages {

    private final Plugin plugin;
    private YamlConfiguration file;
    private YamlConfiguration defaults;

    public Messages(final Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        final File target = new File(this.plugin.getDataFolder(), "messages.yml");
        if (!target.exists()) {
            this.plugin.saveResource("messages.yml", false);
        }
        this.defaults = loadDefaults();
        YamlConfiguration loaded;
        try {
            loaded = YamlConfiguration.loadConfiguration(target);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("messages.yml inválido; usando as mensagens padrão.");
            loaded = new YamlConfiguration();
        }
        loaded.setDefaults(this.defaults);
        this.file = loaded;
    }

    private YamlConfiguration loadDefaults() {
        try (InputStream stream = this.plugin.getResource("messages.yml")) {
            if (stream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return new YamlConfiguration();
        }
    }

    public String raw(final String key) {
        final String value = this.file.getString(key, this.defaults.getString(key));
        return value == null ? key : value;
    }

    public List<String> rawList(final String key) {
        final List<String> value = this.file.getStringList(key);
        if (value.isEmpty()) {
            final List<String> fallback = this.defaults.getStringList(key);
            return fallback.isEmpty() ? Collections.emptyList() : fallback;
        }
        return value;
    }

    public void send(final CommandSender target, final String key) {
        send(target, key, Map.of());
    }

    public void send(final CommandSender target, final String key, final Map<String, String> placeholders) {
        String text = Text.apply(raw(key), placeholders);
        if (target instanceof Player player) {
            text = Placeholders.apply(player, text);
        }
        if (text.isBlank()) {
            return;
        }
        Chat.sendLines(target, text.replace("{nl}", "\n"));
    }

    public List<String> list(final Player player, final String key, final Map<String, String> placeholders) {
        return Placeholders.apply(player, Text.apply(rawList(key), placeholders));
    }

    public String line(final Player player, final String key, final Map<String, String> placeholders) {
        return Placeholders.apply(player, Text.apply(raw(key), placeholders));
    }
}
