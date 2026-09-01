package dev.singlehope.free.shpix.compat;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

final class NbtItemTags implements ItemTags {

    @Override
    public ItemStack write(final ItemStack stack, final String value) {
        try {
            final Class<?> craftStack = ServerCompat.craftClass("inventory.CraftItemStack");
            final Method asNms = craftStack.getMethod("asNMSCopy", ItemStack.class);
            final Method asBukkit = craftStack.getMethod("asBukkitCopy", asNms.getReturnType());

            final Object nmsStack = asNms.invoke(null, stack);
            final Class<?> compoundClass = ServerCompat.nmsClass("NBTTagCompound", "net.minecraft.nbt.NBTTagCompound");

            Object tag = invokeNoArg(nmsStack, "getTag");
            if (tag == null || !compoundClass.isInstance(tag)) {
                tag = compoundClass.getConstructor().newInstance();
            }
            compoundClass.getMethod("setString", String.class, String.class).invoke(tag, KEY, value);
            nmsStack.getClass().getMethod("setTag", compoundClass).invoke(nmsStack, tag);

            return (ItemStack) asBukkit.invoke(null, nmsStack);
        } catch (Exception | LinkageError ignored) {
            return stack;
        }
    }

    @Override
    public String read(final ItemStack stack) {
        if (stack == null || stack.getType().name().equals("AIR")) {
            return null;
        }
        try {
            final Class<?> craftStack = ServerCompat.craftClass("inventory.CraftItemStack");
            final Method asNms = craftStack.getMethod("asNMSCopy", ItemStack.class);
            final Object nmsStack = asNms.invoke(null, stack);

            final Object tag = invokeNoArg(nmsStack, "getTag");
            if (tag == null) {
                return null;
            }
            final Class<?> compoundClass = tag.getClass();
            final boolean present = (boolean) compoundClass.getMethod("hasKey", String.class).invoke(tag, KEY);
            if (!present) {
                return null;
            }
            final String value = (String) compoundClass.getMethod("getString", String.class).invoke(tag, KEY);
            return value == null || value.isEmpty() ? null : value;
        } catch (Exception | LinkageError ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(final Object target, final String name) throws Exception {
        final Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }
}
