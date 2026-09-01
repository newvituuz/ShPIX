package dev.singlehope.free.shpix.shop;

import dev.singlehope.free.shpix.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record RewardItem(Material material, int data, int amount) {

    public ItemStack stack() {
        return new ItemBuilder(this.material, this.amount).durability(this.data).build();
    }
}
