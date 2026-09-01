package dev.singlehope.free.shpix.payment.listener;

import dev.singlehope.free.shpix.payment.OrderItems;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class OrderItemListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (OrderItems.isOrderItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (OrderItems.isOrderItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        final ItemStack stack = event.getHand() == null
                ? null
                : event.getPlayer().getInventory().getItem(event.getHand());
        if (OrderItems.isOrderItem(stack)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        if (OrderItems.isOrderItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (OrderItems.isOrderItem(event.getCurrentItem())
                || OrderItems.isOrderItem(event.getCursor())
                || OrderItems.isOrderItem(event.getHotbarButton() < 0
                        ? null
                        : event.getWhoClicked().getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        if (OrderItems.isOrderItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCraft(final CraftItemEvent event) {
        for (final ItemStack stack : event.getInventory().getMatrix()) {
            if (OrderItems.isOrderItem(stack)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(final PlayerDeathEvent event) {
        event.getDrops().removeIf(OrderItems::isOrderItem);
    }
}
