package dev.singlehope.free.shpix.menu;

import dev.singlehope.free.shpix.ShPixPlugin;
import dev.singlehope.free.shpix.shop.Category;
import dev.singlehope.free.shpix.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CategoryMenu extends PagedMenu {

    private final ShPixPlugin plugin;

    public CategoryMenu(final ShPixPlugin plugin) {
        super("&8Loja Virtual - Categorias");
        this.plugin = plugin;
    }

    @Override
    protected List<Entry> entries(final Player viewer) {
        final List<Entry> entries = new ArrayList<>();
        for (final Category category : this.plugin.catalog().categories()) {
            entries.add(new Entry(category.icon(),
                    event -> new ProductsMenu(this.plugin, category).open(this.plugin, viewer)));
        }
        return entries;
    }

    @Override
    protected ItemStack emptyIcon() {
        return new ItemBuilder("BARRIER").name("&cNenhuma categoria disponível.").build();
    }
}
