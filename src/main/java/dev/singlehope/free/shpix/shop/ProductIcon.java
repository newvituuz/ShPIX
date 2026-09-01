package dev.singlehope.free.shpix.shop;

import dev.singlehope.free.shpix.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record ProductIcon(String name, List<String> description, Material material, int data) {

    public ProductIcon {
        description = List.copyOf(description);
    }

    public ItemStack stack(final List<String> extraLore) {
        final java.util.List<String> lore = new java.util.ArrayList<>(this.description);
        lore.addAll(extraLore);
        return new ItemBuilder(this.material)
                .name(this.name)
                .legacyLore(lore)
                .durability(this.data)
                .build();
    }
}
