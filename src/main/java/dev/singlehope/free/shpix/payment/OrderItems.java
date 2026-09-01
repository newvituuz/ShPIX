package dev.singlehope.free.shpix.payment;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class OrderItems {

    private static NamespacedKey orderKey;

    private OrderItems() {
    }

    public static void init(final Plugin plugin) {
        orderKey = new NamespacedKey(plugin, "order");
    }

    public static NamespacedKey key() {
        return orderKey;
    }

    public static String referenceOf(final ItemStack stack) {
        if (orderKey == null || stack == null || !stack.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(orderKey, PersistentDataType.STRING);
    }

    public static boolean isOrderItem(final ItemStack stack) {
        return referenceOf(stack) != null;
    }

    public static int removeAll(final Player player, final String referenceId) {
        final Inventory inventory = player.getInventory();
        int removed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final String reference = referenceOf(inventory.getItem(slot));
            if (reference == null) {
                continue;
            }
            if (referenceId == null || reference.equals(referenceId)) {
                inventory.setItem(slot, null);
                removed++;
            }
        }
        return removed;
    }

    public static boolean has(final Player player, final String referenceId) {
        final Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (referenceId.equals(referenceOf(inventory.getItem(slot)))) {
                return true;
            }
        }
        return false;
    }

    public static int removeStale(final Player player, final java.util.Set<String> keepReferences) {
        final Inventory inventory = player.getInventory();
        int removed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final String reference = referenceOf(inventory.getItem(slot));
            if (reference != null && !keepReferences.contains(reference)) {
                inventory.setItem(slot, null);
                removed++;
            }
        }
        return removed;
    }

    public static int findFreeSlot(final Player player) {
        final ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().isAir()) {
            return player.getInventory().getHeldItemSlot();
        }
        return player.getInventory().firstEmpty();
    }
}
