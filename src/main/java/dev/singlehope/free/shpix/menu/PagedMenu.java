package dev.singlehope.free.shpix.menu;

import dev.singlehope.free.shpix.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

public abstract class PagedMenu extends Menu {

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final int PREVIOUS_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int CLOSE_SLOT = 49;

    private int page;

    protected PagedMenu(final Component title) {
        super(title, 6);
    }

    protected abstract List<Entry> entries(Player viewer);

    protected abstract ItemStack emptyIcon();

    @Override
    protected void build(final Player viewer) {
        final List<Entry> entries = entries(viewer);
        final int perPage = CONTENT_SLOTS.length;
        final int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(this.page, pages - 1));

        if (entries.isEmpty()) {
            set(CONTENT_SLOTS[0], emptyIcon());
        } else {
            final int offset = this.page * perPage;
            for (int index = 0; index < perPage; index++) {
                final int entryIndex = offset + index;
                if (entryIndex >= entries.size()) {
                    break;
                }
                final Entry entry = entries.get(entryIndex);
                set(CONTENT_SLOTS[index], entry.icon(), entry.action());
            }
        }

        if (this.page > 0) {
            set(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW).name("&aPágina anterior").build(), event -> {
                this.page--;
                refresh(viewer);
            });
        }
        if (this.page < pages - 1) {
            set(NEXT_SLOT, new ItemBuilder(Material.ARROW).name("&aPróxima página").build(), event -> {
                this.page++;
                refresh(viewer);
            });
        }
        set(CLOSE_SLOT, new ItemBuilder(Material.BARRIER).name("&cFechar").build(),
                event -> event.getWhoClicked().closeInventory());
    }

    public record Entry(ItemStack icon, Consumer<InventoryClickEvent> action) {
    }
}
