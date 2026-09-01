package dev.singlehope.free.shpix.payment;

import dev.singlehope.free.shpix.compat.ItemTags;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;

@SuppressWarnings("deprecation")
public final class OrderItems {

    private static volatile ItemTags tags;

    private OrderItems() {
    }

    public static void init(final Plugin plugin) {
        tags = ItemTags.create(plugin);
    }

    public static ItemStack tag(final ItemStack stack, final String referenceId) {
        final ItemTags backend = tags;
        return backend == null ? stack : backend.write(stack, referenceId);
    }

    public static String referenceOf(final ItemStack stack) {
        final ItemTags backend = tags;
        if (backend == null || stack == null) {
            return null;
        }
        return backend.read(stack);
    }

    public static boolean isOrderItem(final ItemStack stack) {
        return referenceOf(stack) != null;
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

    public static int removeStale(final Player player, final Set<String> keepReferences) {
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
        final ItemStack inHand = player.getInventory().getItemInHand();
        if (inHand == null || inHand.getType().name().equals("AIR")) {
            return player.getInventory().getHeldItemSlot();
        }
        return player.getInventory().firstEmpty();
    }
}
