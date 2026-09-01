package dev.singlehope.free.shpix.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(final InventoryClickEvent event) {
        final InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        menu.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu
                && event.getPlayer() instanceof Player player) {
            menu.handleClose(player);
        }
    }
}
