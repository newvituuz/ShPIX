package dev.singlehope.free.shpix.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.singlehope.free.shpix.config.PluginConfig;
import dev.singlehope.free.shpix.http.HttpException;
import dev.singlehope.free.shpix.http.JsonHttpClient;
import dev.singlehope.free.shpix.payment.Order;
import dev.singlehope.free.shpix.scheduler.Schedulers;
import dev.singlehope.free.shpix.util.Money;
import dev.singlehope.free.shpix.util.Text;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DiscordNotifier implements AutoCloseable {

    private static final Set<String> ALLOWED_HOSTS =
            Set.of("discord.com", "discordapp.com", "ptb.discord.com", "canary.discord.com");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ROOT);

    private final Plugin plugin;
    private volatile PluginConfig config;
    private volatile JsonHttpClient http;

    public DiscordNotifier(final Plugin plugin, final PluginConfig config) {
        this.plugin = plugin;
        reload(config);
    }

    public void reload(final PluginConfig newConfig) {
        close();
        this.config = newConfig;
        if (newConfig.discordEnabled()) {
            this.http = new JsonHttpClient("ShPIX-Discord", Duration.ofSeconds(10), 30, ALLOWED_HOSTS);
        }
    }

    public void notifySale(final String playerName, final String productName, final Order order) {
        final PluginConfig current = this.config;
        final JsonHttpClient client = this.http;
        if (!current.discordEnabled() || client == null) {
            return;
        }

        final Map<String, String> values = Map.of(
                "{player}", playerName,
                "{product}", productName,
                "{date}", DATE_FORMAT.format(order.updatedAt().atZone(ZoneId.systemDefault())),
                "{reference}", order.shortReference());

        final JsonObject embed = new JsonObject();
        embed.addProperty("title", Text.apply(current.discordTitle(), values));
        embed.addProperty("description", Text.apply(current.discordDescription(), values));
        embed.addProperty("color", current.discordColor());

        final JsonArray fields = new JsonArray();
        fields.add(field(current.discordFieldPlayer(), playerName, true));
        fields.add(field(current.discordFieldPrice(), "R$ " + Money.format(order.amount()), true));
        fields.add(field(current.discordFieldFee(), current.feePercent().toPlainString() + "%", true));
        embed.add("fields", fields);

        final JsonObject footer = new JsonObject();
        footer.addProperty("text", Text.apply(current.discordFooter(), values));
        embed.add("footer", footer);

        final JsonArray embeds = new JsonArray();
        embeds.add(embed);

        final JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);

        Schedulers.async(this.plugin, () -> {
            try {
                client.post(URI.create(current.discordWebhookUrl()), payload.toString(), Map.of());
            } catch (HttpException | IllegalArgumentException exception) {
                this.plugin.getLogger().warning("Não foi possível enviar a notificação de venda ao Discord.");
            }
        });
    }

    private static JsonObject field(final String name, final String value, final boolean inline) {
        final JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    @Override
    public void close() {
        final JsonHttpClient client = this.http;
        this.http = null;
        if (client != null) {
            client.close();
        }
    }
}
