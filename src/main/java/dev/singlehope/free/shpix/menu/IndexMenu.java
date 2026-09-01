package dev.singlehope.free.shpix.menu;

import dev.singlehope.free.shpix.ShPixPlugin;
import dev.singlehope.free.shpix.scheduler.Schedulers;
import dev.singlehope.free.shpix.user.User;
import dev.singlehope.free.shpix.util.ItemBuilder;
import dev.singlehope.free.shpix.util.Money;
import org.bukkit.entity.Player;

import java.util.List;

public final class IndexMenu extends Menu {

    private final ShPixPlugin plugin;

    public IndexMenu(final ShPixPlugin plugin) {
        super("&8Loja Virtual", 3);
        this.plugin = plugin;
    }

    @Override
    protected void build(final Player viewer) {
        final User user = this.plugin.users().getOrEmpty(viewer.getUniqueId(), viewer.getName());

        set(11, new ItemBuilder("PLAYER_HEAD")
                .name("&aSuas informações")
                .lore(List.of(
                        "&7Visualize suas estatísticas.",
                        "",
                        "  &8&l> &fTotal pago: &2R$&a" + Money.format(user.totalPaid()),
                        "  &8&l> &fTotal de pedidos: &a" + user.totalOrders(),
                        "  &8&l> &fTotal reembolsado: &2R$&a" + Money.format(user.totalRefunded())))
                .build());

        set(13, new ItemBuilder("CHEST")
                .name("&aCategorias")
                .lore(List.of("&7Navegue pelos produtos disponíveis.", "", "&eClique para abrir."))
                .build(),
                event -> new CategoryMenu(this.plugin).open(this.plugin, viewer));

        set(15, new ItemBuilder("BOOK")
                .name("&aMeus pedidos")
                .lore(List.of("&7Veja os seus últimos pedidos.", "", "&eClique para abrir."))
                .build(),
                event -> openOrders(viewer));

        set(22, new ItemBuilder("BARRIER").name("&cFechar").build(),
                event -> viewer.closeInventory());
    }

    private void openOrders(final Player viewer) {
        viewer.closeInventory();
        if (!this.plugin.isStorageReady()) {
            this.plugin.messages().send(viewer, "payment-database-unavailable");
            return;
        }
        Schedulers.async(this.plugin, () -> {
            final var orders = this.plugin.orders().recentOrders(viewer.getUniqueId(), 21);
            new OrdersMenu(this.plugin, orders).open(this.plugin, viewer);
        });
    }
}
