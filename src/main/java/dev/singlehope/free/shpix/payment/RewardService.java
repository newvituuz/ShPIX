package dev.singlehope.free.shpix.payment;

import dev.singlehope.free.shpix.config.Messages;
import dev.singlehope.free.shpix.notification.DiscordNotifier;
import dev.singlehope.free.shpix.payment.event.PaymentCompletedEvent;
import dev.singlehope.free.shpix.payment.event.PaymentExpiredEvent;
import dev.singlehope.free.shpix.scheduler.Schedulers;
import dev.singlehope.free.shpix.shop.Product;
import dev.singlehope.free.shpix.shop.RewardItem;
import dev.singlehope.free.shpix.shop.ShopCatalog;
import dev.singlehope.free.shpix.shop.action.ActionType;
import dev.singlehope.free.shpix.shop.action.ProductAction;
import dev.singlehope.free.shpix.util.Money;
import dev.singlehope.free.shpix.util.Text;
import dev.singlehope.free.shpix.compat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class RewardService {

    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,32}$");
    private static final int FADE_IN = 10;
    private static final int STAY = 60;
    private static final int FADE_OUT = 10;

    private final Plugin plugin;
    private final ShopCatalog catalog;
    private final Messages messages;
    private final DiscordNotifier discord;

    public RewardService(final Plugin plugin, final ShopCatalog catalog, final Messages messages,
                         final DiscordNotifier discord) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.messages = messages;
        this.discord = discord;
    }

    public void deliver(final Player player, final Order order) {
        run(order, () -> OrderItems.removeAll(player, order.referenceId()));

        final Optional<Product> product = this.catalog.product(order.productId());
        if (product.isEmpty()) {
            this.plugin.getLogger().severe("Pedido " + order.shortReference() + " pago mas o produto "
                    + order.productId() + " não existe mais. Entregue manualmente.");
            run(order, () -> this.messages.send(player, "delivery-product-missing",
                    placeholders(player, order, order.productId())));
            return;
        }

        final Product resolved = product.get();
        run(order, () -> giveItems(player, resolved.rewards().items()));
        run(order, () -> dispatchCommands(player, resolved.rewards().commands(), order));
        run(order, () -> announce(player, resolved, order, ActionType.COLLECT));
        run(order, () -> this.discord.notifySale(player.getName(), resolved.name(), order));
        run(order, () -> Bukkit.getPluginManager().callEvent(new PaymentCompletedEvent(player, order)));
    }

    private void run(final Order order, final Runnable step) {
        try {
            step.run();
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE,
                    "Erro durante a entrega do pedido " + order.shortReference() + ".", exception);
        }
    }

    public void notifyClosed(final Player player, final Order order) {
        run(order, () -> OrderItems.removeAll(player, order.referenceId()));
        final Optional<Product> product = this.catalog.product(order.productId());
        final ActionType type = order.status() == OrderStatus.REFUNDED ? ActionType.REFUND : ActionType.EXPIRED;
        if (product.isPresent()) {
            run(order, () -> announce(player, product.get(), order, type));
        } else {
            run(order, () -> this.messages.send(player, "payment-expired",
                    placeholders(player, order, order.productId())));
        }
        run(order, () -> Bukkit.getPluginManager().callEvent(new PaymentExpiredEvent(player, order)));
    }

    private void giveItems(final Player player, final Collection<RewardItem> items) {
        for (final RewardItem item : items) {
            final ItemStack stack = item.stack();
            player.getInventory().addItem(stack).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private void dispatchCommands(final Player player, final Collection<String> commands, final Order order) {
        if (commands.isEmpty()) {
            return;
        }
        final String name = player.getName();
        if (!SAFE_NAME.matcher(name).matches()) {
            this.plugin.getLogger().warning("Comandos do pedido " + order.shortReference()
                    + " não executados: nome de jogador inesperado.");
            return;
        }
        final Map<String, String> values = Map.of(
                "{player}", name,
                "{uuid}", player.getUniqueId().toString(),
                "{amount}", Money.toPlainString(order.amount()),
                "{reference}", order.referenceId());
        Schedulers.global(this.plugin, () -> {
            for (final String command : commands) {
                final String resolved = Text.apply(command, values);
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                } catch (Exception exception) {
                    this.plugin.getLogger().warning("Falha ao executar um comando de recompensa do pedido "
                            + order.shortReference() + ".");
                }
            }
        });
    }

    private void announce(final Player player, final Product product, final Order order, final ActionType type) {
        final Optional<ProductAction> action = product.action(type);
        if (action.isEmpty()) {
            return;
        }
        final ProductAction resolved = action.get();
        final Map<String, String> values = placeholders(player, order, product.name());

        if (resolved.sound() != null) {
            player.playSound(player.getLocation(), resolved.sound(), 1.0F, 1.0F);
        }
        if (!resolved.title().isBlank() || !resolved.subtitle().isBlank()) {
            Chat.title(player,
                    Text.apply(resolved.title(), values),
                    Text.apply(resolved.subtitle(), values),
                    FADE_IN, STAY, FADE_OUT);
        }
        if (!resolved.actionBar().isBlank()) {
            Chat.actionBar(player, Text.apply(resolved.actionBar(), values));
        }
        if (!resolved.message().isBlank()) {
            Chat.sendLines(player, Text.apply(resolved.message(), values));
        }
    }

    private Map<String, String> placeholders(final Player player, final Order order, final String productName) {
        return Map.of(
                "{player}", player.getName(),
                "{displayName}", player.getName(),
                "{product}", productName == null ? "" : productName,
                "{amount}", Money.format(order.amount()),
                "{reference}", order.shortReference());
    }
}
