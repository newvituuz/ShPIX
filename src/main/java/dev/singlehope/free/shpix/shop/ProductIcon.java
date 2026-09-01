package dev.singlehope.free.shpix.shop;

import dev.singlehope.free.shpix.util.ItemBuilder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record ProductIcon(String name, List<String> description, String material, int data) {

    public ProductIcon {
        description = List.copyOf(description);
    }

    public ItemStack stack(final List<String> extraLore) {
        final List<String> lore = new ArrayList<>(this.description);
        lore.addAll(extraLore);
        return new ItemBuilder(this.material)
                .name(this.name)
                .lore(lore)
                .durability(this.data)
                .build();
    }
}
