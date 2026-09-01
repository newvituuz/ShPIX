package dev.singlehope.free.shpix.util;

import dev.singlehope.free.shpix.compat.Materials;
import dev.singlehope.free.shpix.compat.Skulls;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

@SuppressWarnings("deprecation")
public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(final String materialName) {
        this(materialName, 1);
    }

    public ItemBuilder(final String materialName, final int amount) {
        final Materials.Resolved resolved = Materials.resolve(materialName);
        this.stack = new ItemStack(resolved.material(), clampAmount(amount), resolved.data());
        this.meta = this.stack.getItemMeta();
        applyFlags();
    }

    public ItemBuilder(final Material material, final short data, final int amount) {
        this.stack = new ItemStack(material == null ? Material.BARRIER : material, clampAmount(amount), data);
        this.meta = this.stack.getItemMeta();
        applyFlags();
    }

    private void applyFlags() {
        if (this.meta == null) {
            return;
        }
        for (final String name : new String[]{"HIDE_ATTRIBUTES", "HIDE_ENCHANTS", "HIDE_DESTROYS",
                "HIDE_PLACED_ON", "HIDE_UNBREAKABLE"}) {
            try {
                this.meta.addItemFlags(ItemFlag.valueOf(name));
            } catch (IllegalArgumentException | NoSuchFieldError ignored) {
                // flag inexistente nesta versão
            }
        }
    }

    private static int clampAmount(final int amount) {
        return Math.max(1, Math.min(64, amount));
    }

    public ItemBuilder amount(final int amount) {
        this.stack.setAmount(clampAmount(amount));
        return this;
    }

    public ItemBuilder name(final String legacy) {
        if (this.meta != null) {
            this.meta.setDisplayName(Text.color(legacy));
        }
        return this;
    }

    public ItemBuilder lore(final List<String> legacy) {
        if (this.meta != null) {
            this.meta.setLore(Text.colorAll(legacy));
        }
        return this;
    }

    public ItemBuilder durability(final int data) {
        if (data > 0) {
            this.stack.setDurability((short) data);
        }
        return this;
    }

    public ItemBuilder texture(final String base64) {
        Skulls.applyTexture(this.meta, base64);
        return this;
    }

    public ItemStack build() {
        if (this.meta != null) {
            this.stack.setItemMeta(this.meta);
        }
        return this.stack;
    }
}
