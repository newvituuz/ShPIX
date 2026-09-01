package dev.singlehope.free.shpix.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(final Material material) {
        this(material, 1);
    }

    public ItemBuilder(final Material material, final int amount) {
        this.stack = new ItemStack(material == null ? Material.BARRIER : material, clampAmount(amount));
        this.meta = this.stack.getItemMeta();
        if (this.meta != null) {
            this.meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_UNBREAKABLE);
        }
    }

    private static int clampAmount(final int amount) {
        return Math.max(1, Math.min(64, amount));
    }

    public ItemBuilder amount(final int amount) {
        this.stack.setAmount(clampAmount(amount));
        return this;
    }

    public ItemBuilder name(final Component name) {
        if (this.meta != null) {
            this.meta.displayName(name);
        }
        return this;
    }

    public ItemBuilder name(final String legacy) {
        return name(Text.item(legacy));
    }

    public ItemBuilder lore(final List<Component> lore) {
        if (this.meta != null) {
            this.meta.lore(lore);
        }
        return this;
    }

    public ItemBuilder legacyLore(final List<String> lore) {
        return lore(Text.itemLore(lore));
    }

    public ItemBuilder durability(final int data) {
        if (data > 0 && this.meta instanceof Damageable damageable) {
            damageable.setDamage(data);
        }
        return this;
    }

    public ItemBuilder tag(final NamespacedKey key, final String value) {
        if (this.meta != null) {
            this.meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
        return this;
    }

    public ItemBuilder texture(final String base64) {
        if (!(this.meta instanceof SkullMeta skullMeta) || base64 == null || base64.isBlank()) {
            return this;
        }
        try {
            final UUID id = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
            final PlayerProfile profile = Bukkit.createProfile(id, null);
            profile.setProperty(new ProfileProperty("textures", base64));
            skullMeta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // textura inválida: mantém a cabeça padrão
        }
        return this;
    }

    public ItemStack build() {
        if (this.meta != null) {
            this.stack.setItemMeta(this.meta);
        }
        return this.stack;
    }
}
