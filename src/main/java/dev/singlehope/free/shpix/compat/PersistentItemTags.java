package dev.singlehope.free.shpix.compat;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class PersistentItemTags implements ItemTags {

    private final NamespacedKey key;

    PersistentItemTags(final Plugin plugin) {
        this.key = new NamespacedKey(plugin, "order");
    }

    @Override
    public ItemStack write(final ItemStack stack, final String value) {
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public String read(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = stack.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
    }
}
