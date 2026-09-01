package dev.singlehope.free.shpix.compat;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public final class Chat {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final int TITLE_LIMIT = 32;

    private static volatile Method spigotActionBar;
    private static volatile boolean actionBarChecked;
    private static volatile Method apiTitle;
    private static volatile boolean titleChecked;

    private Chat() {
    }

    public static String color(final String raw) {
        if (raw == null) {
            return "";
        }
        final String hexApplied = ServerCompat.atLeast(16) ? applyHex(raw) : HEX.matcher(raw).replaceAll("");
        return ChatColor.translateAlternateColorCodes('&', hexApplied.replace('§', '&'));
    }

    private static String applyHex(final String raw) {
        final Matcher matcher = HEX.matcher(raw);
        final StringBuilder builder = new StringBuilder(raw.length() + 16);
        while (matcher.find()) {
            final String digits = matcher.group(1);
            final StringBuilder replacement = new StringBuilder("&x");
            for (int index = 0; index < digits.length(); index++) {
                replacement.append('&').append(digits.charAt(index));
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    public static String strip(final String raw) {
        return ChatColor.stripColor(color(raw));
    }

    public static String truncate(final String raw, final int limit) {
        final String colored = color(raw);
        if (colored.length() <= limit) {
            return colored;
        }
        int end = limit;
        if (colored.charAt(end - 1) == ChatColor.COLOR_CHAR) {
            end--;
        }
        return colored.substring(0, end);
    }

    public static String inventoryTitle(final String raw) {
        return ServerCompat.atLeast(21) ? color(raw) : truncate(raw, TITLE_LIMIT);
    }

    public static void send(final CommandSender target, final String raw) {
        final String colored = color(raw);
        if (colored.isEmpty()) {
            return;
        }
        target.sendMessage(colored);
    }

    public static void sendLines(final CommandSender target, final String raw) {
        for (final String line : color(raw).split("\n", -1)) {
            target.sendMessage(line);
        }
    }

    public static void sendClickable(final Player player, final String raw, final String hover,
                                     final Click click, final String value) {
        final String colored = color(raw);
        if (colored.isEmpty()) {
            return;
        }
        try {
            final TextComponent root = new TextComponent(TextComponent.fromLegacyText(colored));
            if (hover != null && !hover.isBlank()) {
                root.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        TextComponent.fromLegacyText(color(hover))));
            }
            if (value != null && !value.isBlank()) {
                root.setClickEvent(new ClickEvent(click.resolve(), value));
            }
            player.spigot().sendMessage(new BaseComponent[]{root});
        } catch (Exception | LinkageError ignored) {
            player.sendMessage(colored);
        }
    }

    public static void actionBar(final Player player, final String raw) {
        final String colored = color(raw);
        if (colored.isEmpty()) {
            return;
        }
        if (sendActionBarViaApi(player, colored)) {
            return;
        }
        if (!LegacyPackets.actionBar(player, colored)) {
            player.sendMessage(colored);
        }
    }

    private static boolean sendActionBarViaApi(final Player player, final String colored) {
        if (!actionBarChecked) {
            synchronized (Chat.class) {
                if (!actionBarChecked) {
                    actionBarChecked = true;
                    try {
                        final Class<?> messageType = Class.forName("net.md_5.bungee.api.ChatMessageType");
                        spigotActionBar = player.spigot().getClass()
                                .getMethod("sendMessage", messageType, BaseComponent[].class);
                    } catch (Exception | LinkageError ignored) {
                        spigotActionBar = null;
                    }
                }
            }
        }
        final Method method = spigotActionBar;
        if (method == null) {
            return false;
        }
        try {
            final Class<?> messageType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBarConstant = null;
            for (final Object constant : messageType.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("ACTION_BAR")) {
                    actionBarConstant = constant;
                    break;
                }
            }
            if (actionBarConstant == null) {
                return false;
            }
            method.invoke(player.spigot(), actionBarConstant, TextComponent.fromLegacyText(colored));
            return true;
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    public static void title(final Player player, final String title, final String subtitle,
                             final int fadeIn, final int stay, final int fadeOut) {
        final String coloredTitle = color(title);
        final String coloredSubtitle = color(subtitle);
        if (coloredTitle.isEmpty() && coloredSubtitle.isEmpty()) {
            return;
        }
        if (sendTitleViaApi(player, coloredTitle, coloredSubtitle, fadeIn, stay, fadeOut)) {
            return;
        }
        LegacyPackets.title(player, coloredTitle, coloredSubtitle, fadeIn, stay, fadeOut);
    }

    private static boolean sendTitleViaApi(final Player player, final String title, final String subtitle,
                                           final int fadeIn, final int stay, final int fadeOut) {
        if (!titleChecked) {
            synchronized (Chat.class) {
                if (!titleChecked) {
                    titleChecked = true;
                    try {
                        apiTitle = Player.class.getMethod("sendTitle", String.class, String.class,
                                int.class, int.class, int.class);
                    } catch (NoSuchMethodException | LinkageError ignored) {
                        apiTitle = null;
                    }
                }
            }
        }
        final Method method = apiTitle;
        if (method == null) {
            return false;
        }
        try {
            method.invoke(player, title, subtitle, fadeIn, stay, fadeOut);
            return true;
        } catch (Exception | LinkageError ignored) {
            return false;
        }
    }

    public enum Click {

        OPEN_URL,
        COPY_TEXT;

        private ClickEvent.Action resolve() {
            if (this == OPEN_URL) {
                return ClickEvent.Action.OPEN_URL;
            }
            try {
                return ClickEvent.Action.valueOf("COPY_TO_CLIPBOARD");
            } catch (IllegalArgumentException ignored) {
                return ClickEvent.Action.SUGGEST_COMMAND;
            }
        }
    }
}
