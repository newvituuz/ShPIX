package dev.singlehope.free.shpix.menu;

import dev.singlehope.free.shpix.scheduler.Schedulers;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class Menu implements InventoryHolder {

    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private final Inventory inventory;

    protected Menu(final Component title, final int rows) {
        final int size = Math.max(1, Math.min(6, rows)) * 9;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    protected abstract void build(Player viewer);

    public final void open(final Plugin plugin, final Player player) {
        Schedulers.entity(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            refresh(player);
            player.openInventory(this.inventory);
        });
    }

    public final void refresh(final Player viewer) {
        this.actions.clear();
        this.inventory.clear();
        build(viewer);
    }

    protected final void set(final int slot, final ItemStack stack) {
        set(slot, stack, null);
    }

    protected final void set(final int slot, final ItemStack stack, final Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= this.inventory.getSize()) {
            return;
        }
        this.inventory.setItem(slot, stack);
        if (action == null) {
            this.actions.remove(slot);
        } else {
            this.actions.put(slot, action);
        }
    }

    public final void handleClick(final InventoryClickEvent event) {
        final Consumer<InventoryClickEvent> action = this.actions.get(event.getRawSlot());
        if (action == null) {
            return;
        }
        try {
            action.accept(event);
        } catch (Exception ignored) {
            event.getWhoClicked().closeInventory();
        }
    }

    public void handleClose(final Player player) {
        // sobrescrito pelos menus que precisam liberar recursos
    }

    @Override
    public final @NotNull Inventory getInventory() {
        return this.inventory;
    }
}
