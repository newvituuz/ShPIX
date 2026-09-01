package dev.singlehope.free.shpix.compat;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public interface ItemTags {

    String KEY = "shpix_order";

    static ItemTags create(final Plugin plugin) {
        if (ServerCompat.hasPersistentData()) {
            try {
                return new PersistentItemTags(plugin);
            } catch (Exception | LinkageError ignored) {
                // cai para o backend legado
            }
        }
        return new NbtItemTags();
    }

    ItemStack write(ItemStack stack, String value);

    String read(ItemStack stack);
}
