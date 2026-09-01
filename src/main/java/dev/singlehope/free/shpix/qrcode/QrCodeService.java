package dev.singlehope.free.shpix.qrcode;

import dev.singlehope.free.shpix.compat.Chat;
import dev.singlehope.free.shpix.compat.Maps;
import dev.singlehope.free.shpix.config.Messages;
import dev.singlehope.free.shpix.payment.Order;
import dev.singlehope.free.shpix.payment.OrderItems;
import dev.singlehope.free.shpix.scheduler.Schedulers;
import dev.singlehope.free.shpix.util.ItemBuilder;
import dev.singlehope.free.shpix.util.Money;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public final class QrCodeService {

    private final Plugin plugin;
    private final Messages messages;

    public QrCodeService(final Plugin plugin, final Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void deliverQrCode(final Player player, final Order order, final Consumer<Boolean> callback) {
        Schedulers.async(this.plugin, () -> {
            final File logo = new File(this.plugin.getDataFolder(), "logo.png");
            final BufferedImage image = QrCodeImage.render(order.pixCode(), logo, this.plugin.getLogger());
            Schedulers.entity(this.plugin, player,
                    () -> callback.accept(give(player, order, image)),
                    () -> callback.accept(false));
        });
    }

    private boolean give(final Player player, final Order order, final BufferedImage image) {
        if (!player.isOnline()) {
            return false;
        }
        if (OrderItems.has(player, order.referenceId())) {
            return true;
        }
        final int slot = OrderItems.findFreeSlot(player);
        if (slot < 0) {
            this.messages.send(player, "inventory-full");
            return false;
        }

        final Map<String, String> values = placeholders(order);
        ItemStack item = new ItemBuilder("FILLED_MAP")
                .name(this.messages.line(player, "item-qrcode-name", values))
                .lore(this.messages.list(player, "item-qrcode-description", values))
                .build();

        if (image != null) {
            item = applyMap(player, item, image);
        }
        item = OrderItems.tag(item, order.referenceId());

        player.getInventory().setItem(slot, item);
        sendPixMessage(player, order, values);
        return true;
    }

    private ItemStack applyMap(final Player player, final ItemStack item, final BufferedImage image) {
        try {
            final MapView view = Bukkit.createMap(player.getWorld());
            Maps.disableTracking(view);
            for (final MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
                view.removeRenderer(renderer);
            }
            view.addRenderer(new QrCodeRenderer(image));
            return Maps.attach(item, view);
        } catch (Exception | LinkageError exception) {
            this.plugin.getLogger().warning("Não foi possível renderizar o mapa do QR Code nesta versão do servidor.");
            return item;
        }
    }

    private void sendPixMessage(final Player player, final Order order, final Map<String, String> values) {
        final String pixCode = order.pixCode();
        if (pixCode != null && !pixCode.isBlank()) {
            Chat.sendClickable(player,
                    this.messages.line(player, "success-payment-code", values),
                    this.messages.line(player, "success-payment-code-hover", values),
                    Chat.Click.COPY_TEXT, pixCode);
        }

        final String ticketUrl = order.ticketUrl();
        if (ticketUrl != null && !ticketUrl.isBlank()) {
            Chat.sendClickable(player,
                    this.messages.line(player, "success-payment-link", values),
                    this.messages.line(player, "success-payment-link-hover", values),
                    Chat.Click.OPEN_URL, ticketUrl);
        }
    }

    private Map<String, String> placeholders(final Order order) {
        final long minutes = Math.max(1L, Duration.between(Instant.now(), order.expiresAt()).toMinutes());
        return Map.of(
                "{amount}", Money.format(order.amount()),
                "{minutes}", String.valueOf(minutes),
                "{reference}", order.shortReference());
    }
}
