package dev.singlehope.free.shpix.qrcode;

import dev.singlehope.free.shpix.config.Messages;
import dev.singlehope.free.shpix.payment.Order;
import dev.singlehope.free.shpix.payment.OrderItems;
import dev.singlehope.free.shpix.scheduler.Schedulers;
import dev.singlehope.free.shpix.util.ItemBuilder;
import dev.singlehope.free.shpix.util.Money;
import dev.singlehope.free.shpix.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

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
        final ItemStack item = new ItemBuilder(Material.FILLED_MAP)
                .name(this.messages.line(player, "item-qrcode-name", values))
                .legacyLore(this.messages.list(player, "item-qrcode-description", values))
                .tag(OrderItems.key(), order.referenceId())
                .build();

        if (image != null && item.getItemMeta() instanceof MapMeta meta) {
            final MapView view = Bukkit.createMap(player.getWorld());
            view.setTrackingPosition(false);
            new java.util.ArrayList<>(view.getRenderers()).forEach(view::removeRenderer);
            view.addRenderer(new QrCodeRenderer(image));
            meta.setMapView(view);
            item.setItemMeta(meta);
        }

        player.getInventory().setItem(slot, item);
        sendPixMessage(player, order, values);
        return true;
    }

    private void sendPixMessage(final Player player, final Order order, final Map<String, String> values) {
        Component message = Text.color(this.messages.line(player, "success-payment-code", values));
        final String pixCode = order.pixCode();
        if (pixCode != null && !pixCode.isBlank()) {
            message = message
                    .hoverEvent(HoverEvent.showText(Text.color(this.messages.line(player, "success-payment-code-hover", values))))
                    .clickEvent(ClickEvent.copyToClipboard(pixCode));
        }
        player.sendMessage(message);

        final String ticketUrl = order.ticketUrl();
        if (ticketUrl != null && !ticketUrl.isBlank()) {
            player.sendMessage(Text.color(this.messages.line(player, "success-payment-link", values))
                    .hoverEvent(HoverEvent.showText(Text.color(this.messages.line(player, "success-payment-link-hover", values))))
                    .clickEvent(ClickEvent.openUrl(ticketUrl)));
        }
    }

    private Map<String, String> placeholders(final Order order) {
        final long minutes = Math.max(1L,
                Duration.between(Instant.now(), order.expiresAt()).toMinutes());
        return Map.of(
                "{amount}", Money.format(order.amount()),
                "{minutes}", String.valueOf(minutes),
                "{reference}", order.shortReference());
    }
}
