package dev.singlehope.free.shpix.compat;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class LegacyPackets {

    private static volatile boolean unavailable;

    private LegacyPackets() {
    }

    static boolean actionBar(final Player player, final String legacy) {
        if (unavailable) {
            return false;
        }
        try {
            final Object component = chatComponent(legacy);
            final Class<?> baseComponent = ServerCompat.nmsClass("IChatBaseComponent", "net.minecraft.network.chat.IChatBaseComponent");
            final Class<?> packetClass = ServerCompat.nmsClass("PacketPlayOutChat", "net.minecraft.network.protocol.game.PacketPlayOutChat");
            final Constructor<?> constructor = packetClass.getConstructor(baseComponent, byte.class);
            sendPacket(player, constructor.newInstance(component, (byte) 2));
            return true;
        } catch (Exception | LinkageError exception) {
            unavailable = true;
            return false;
        }
    }

    static boolean title(final Player player, final String title, final String subtitle,
                         final int fadeIn, final int stay, final int fadeOut) {
        if (unavailable) {
            return false;
        }
        try {
            final Class<?> baseComponent = ServerCompat.nmsClass("IChatBaseComponent", "net.minecraft.network.chat.IChatBaseComponent");
            final Class<?> packetClass = ServerCompat.nmsClass("PacketPlayOutTitle", "net.minecraft.network.protocol.game.PacketPlayOutTitle");
            final Class<?> actionEnum = Class.forName(packetClass.getName() + "$EnumTitleAction");

            final Constructor<?> timesConstructor = packetClass.getConstructor(actionEnum, baseComponent, int.class, int.class, int.class);
            sendPacket(player, timesConstructor.newInstance(enumValue(actionEnum, "TIMES"), null, fadeIn, stay, fadeOut));

            final Constructor<?> textConstructor = packetClass.getConstructor(actionEnum, baseComponent);
            if (title != null && !title.isEmpty()) {
                sendPacket(player, textConstructor.newInstance(enumValue(actionEnum, "TITLE"), chatComponent(title)));
            }
            if (subtitle != null && !subtitle.isEmpty()) {
                sendPacket(player, textConstructor.newInstance(enumValue(actionEnum, "SUBTITLE"), chatComponent(subtitle)));
            }
            return true;
        } catch (Exception | LinkageError exception) {
            unavailable = true;
            return false;
        }
    }

    private static Object enumValue(final Class<?> type, final String name) {
        for (final Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalStateException("constante ausente: " + name);
    }

    private static Object chatComponent(final String legacy) throws Exception {
        final Class<?> baseComponent = ServerCompat.nmsClass("IChatBaseComponent", "net.minecraft.network.chat.IChatBaseComponent");
        final Class<?> serializer = Class.forName(baseComponent.getName() + "$ChatSerializer");
        for (final Method method : serializer.getMethods()) {
            if (method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class
                    && baseComponent.isAssignableFrom(method.getReturnType())) {
                return method.invoke(null, "{\"text\":\"" + escape(legacy) + "\"}");
            }
        }
        throw new IllegalStateException("serializer indisponível");
    }

    private static String escape(final String raw) {
        final StringBuilder builder = new StringBuilder(raw.length() + 8);
        for (int index = 0; index < raw.length(); index++) {
            final char character = raw.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static void sendPacket(final Player player, final Object packet) throws Exception {
        final Object handle = player.getClass().getMethod("getHandle").invoke(player);
        final Object connection = connectionOf(handle);
        for (final Method method : connection.getClass().getMethods()) {
            if (method.getName().equals("sendPacket") && method.getParameterCount() == 1) {
                method.invoke(connection, packet);
                return;
            }
        }
        throw new IllegalStateException("sendPacket indisponível");
    }

    private static Object connectionOf(final Object handle) throws Exception {
        for (final Field field : handle.getClass().getFields()) {
            if (field.getName().equals("playerConnection") || field.getName().equals("connection")) {
                return field.get(handle);
            }
        }
        throw new IllegalStateException("playerConnection indisponível");
    }
}
