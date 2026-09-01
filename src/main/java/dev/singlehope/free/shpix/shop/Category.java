package dev.singlehope.free.shpix.shop;

import dev.singlehope.free.shpix.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record Category(String id, String name, List<String> description, Material material, int data) {

    public Category {
        description = List.copyOf(description);
    }

    public ItemStack icon() {
        return new ItemBuilder(this.material)
                .name(this.name)
                .legacyLore(this.description)
                .durability(this.data)
                .build();
    }
}
