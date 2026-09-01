package dev.singlehope.free.shpix.compat;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Skulls {

    private static volatile boolean legacyUnavailable;

    private Skulls() {
    }

    public static void applyTexture(final ItemMeta meta, final String base64) {
        if (!(meta instanceof SkullMeta skullMeta) || base64 == null || base64.isBlank()) {
            return;
        }
        if (ServerCompat.hasPaperProfile() && PaperSkulls.apply(skullMeta, base64)) {
            return;
        }
        applyLegacy(skullMeta, base64);
    }

    private static void applyLegacy(final SkullMeta meta, final String base64) {
        if (legacyUnavailable) {
            return;
        }
        try {
            final Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            final Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            final UUID id = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
            final Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(id, null);

            final Object properties = profileClass.getMethod("getProperties").invoke(profile);
            final Object property = newProperty(propertyClass, base64);
            for (final Method method : properties.getClass().getMethods()) {
                if (method.getName().equals("put") && method.getParameterCount() == 2) {
                    method.invoke(properties, "textures", property);
                    break;
                }
            }

            final Field field = meta.getClass().getDeclaredField("profile");
            field.setAccessible(true);
            field.set(meta, profile);
        } catch (Exception | LinkageError ignored) {
            legacyUnavailable = true;
        }
    }

    private static Object newProperty(final Class<?> propertyClass, final String base64) throws Exception {
        try {
            return propertyClass.getConstructor(String.class, String.class).newInstance("textures", base64);
        } catch (NoSuchMethodException ignored) {
            return propertyClass.getConstructor(String.class, String.class, String.class)
                    .newInstance("textures", base64, null);
        }
    }
}
