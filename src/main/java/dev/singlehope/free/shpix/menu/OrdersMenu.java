package dev.singlehope.free.shpix.menu;

import dev.singlehope.free.shpix.ShPixPlugin;
import dev.singlehope.free.shpix.payment.Order;
import dev.singlehope.free.shpix.payment.OrderStatus;
import dev.singlehope.free.shpix.util.ItemBuilder;
import dev.singlehope.free.shpix.util.Money;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OrdersMenu extends PagedMenu {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    private final ShPixPlugin plugin;
    private final List<Order> orders;

    public OrdersMenu(final ShPixPlugin plugin, final List<Order> orders) {
        super("&8Loja - Meus pedidos");
        this.plugin = plugin;
        this.orders = List.copyOf(orders);
    }

    @Override
    protected List<Entry> entries(final Player viewer) {
        final List<Entry> entries = new ArrayList<>();
        for (final Order order : this.orders) {
            final String productName = this.plugin.catalog().product(order.productId())
                    .map(product -> product.name())
                    .orElse(order.productId());
            final ItemStack icon = new ItemBuilder(iconFor(order.status()))
                    .name("&f" + productName)
                    .lore(List.of(
                            "",
                            "  &8&l> &fCódigo: &7" + order.shortReference(),
                            "  &8&l> &fValor: &2R$&a" + Money.format(order.amount()),
                            "  &8&l> &fEstado: " + label(order.status()),
                            "  &8&l> &fData: &7" + DATE_FORMAT.format(order.createdAt().atZone(ZoneId.systemDefault()))))
                    .build();
            entries.add(new Entry(icon, null));
        }
        return entries;
    }

    private static String iconFor(final OrderStatus status) {
        return switch (status) {
            case DELIVERED, PAID -> "EMERALD";
            case WAITING -> "CLOCK";
            case REFUNDED -> "GOLD_INGOT";
            case EXPIRED, CANCELLED -> "REDSTONE";
        };
    }

    private static String label(final OrderStatus status) {
        return switch (status) {
            case DELIVERED -> "&aEntregue";
            case PAID -> "&aPago";
            case WAITING -> "&eAguardando pagamento";
            case REFUNDED -> "&6Reembolsado";
            case EXPIRED -> "&cExpirado";
            case CANCELLED -> "&cCancelado";
        };
    }

    @Override
    protected ItemStack emptyIcon() {
        return new ItemBuilder("BARRIER").name("&cVocê ainda não fez nenhum pedido.").build();
    }
}
