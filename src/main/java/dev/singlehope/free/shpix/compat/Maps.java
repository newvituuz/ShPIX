package dev.singlehope.free.shpix.compat;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapView;

import java.lang.reflect.Method;

@SuppressWarnings("deprecation")
public final class Maps {

    private Maps() {
    }

    public static int idOf(final MapView view) {
        try {
            final Method method = MapView.class.getMethod("getId");
            final Object value = method.invoke(view);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (Exception | LinkageError ignored) {
            return 0;
        }
    }

    public static void disableTracking(final MapView view) {
        try {
            MapView.class.getMethod("setTrackingPosition", boolean.class).invoke(view, false);
        } catch (Exception | LinkageError ignored) {
            // indisponível antes da 1.9
        }
    }

    public static ItemStack attach(final ItemStack stack, final MapView view) {
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null && bindMeta(meta, view)) {
            stack.setItemMeta(meta);
            return stack;
        }
        stack.setDurability((short) idOf(view));
        return stack;
    }

    private static boolean bindMeta(final ItemMeta meta, final MapView view) {
        try {
            final Method setMapView = meta.getClass().getMethod("setMapView", MapView.class);
            setMapView.invoke(meta, view);
            return true;
        } catch (Exception | LinkageError ignored) {
            // tenta o identificador numérico
        }
        try {
            final Method setMapId = meta.getClass().getMethod("setMapId", int.class);
            setMapId.invoke(meta, idOf(view));
            return true;
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }
}
