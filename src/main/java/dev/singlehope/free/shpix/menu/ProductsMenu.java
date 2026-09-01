package dev.singlehope.free.shpix.menu;

import dev.singlehope.free.shpix.ShPixPlugin;
import dev.singlehope.free.shpix.shop.Category;
import dev.singlehope.free.shpix.shop.Product;
import dev.singlehope.free.shpix.util.ItemBuilder;
import dev.singlehope.free.shpix.util.Money;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ProductsMenu extends PagedMenu {

    private final ShPixPlugin plugin;
    private final Category category;

    public ProductsMenu(final ShPixPlugin plugin, final Category category) {
        super("&8Loja - " + category.name());
        this.plugin = plugin;
        this.category = category;
    }

    @Override
    protected List<Entry> entries(final Player viewer) {
        final List<Entry> entries = new ArrayList<>();
        for (final Product product : this.plugin.catalog().productsOf(this.category)) {
            final List<String> extra = List.of(
                    "",
                    "&8&l> &fPreço: &2R$&a" + Money.format(product.price()),
                    "",
                    "&eClique para comprar.");
            entries.add(new Entry(product.icon().stack(extra),
                    event -> new CheckoutMenu(this.plugin, product, null).open(this.plugin, viewer)));
        }
        return entries;
    }

    @Override
    protected ItemStack emptyIcon() {
        return new ItemBuilder("BARRIER").name("&cNão existem produtos disponíveis nesta categoria.").build();
    }
}
