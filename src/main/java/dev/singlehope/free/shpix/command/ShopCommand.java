package dev.singlehope.free.shpix.command;

import dev.singlehope.free.shpix.ShPixPlugin;
import dev.singlehope.free.shpix.menu.CategoryMenu;
import dev.singlehope.free.shpix.menu.IndexMenu;
import dev.singlehope.free.shpix.menu.ProductsMenu;
import dev.singlehope.free.shpix.shop.Category;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ShopCommand implements TabExecutor {

    private final ShPixPlugin plugin;

    public ShopCommand(final ShPixPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender, final @NotNull Command command,
                             final @NotNull String label, final String[] args) {
        if (!(sender instanceof Player player)) {
            this.plugin.messages().send(sender, "command-player-only");
            return true;
        }

        if (args.length == 0) {
            new IndexMenu(this.plugin).open(this.plugin, player);
            return true;
        }

        final String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("categorias") || action.equals("categories")) {
            new CategoryMenu(this.plugin).open(this.plugin, player);
            return true;
        }

        if (action.equals("categoria") || action.equals("category")) {
            if (args.length < 2) {
                this.plugin.messages().send(player, "command-shop-category-usage");
                return true;
            }
            final Optional<Category> category = this.plugin.catalog().category(args[1]);
            if (category.isEmpty()) {
                this.plugin.messages().send(player, "command-shop-category-not-found");
                return true;
            }
            new ProductsMenu(this.plugin, category.get()).open(this.plugin, player);
            return true;
        }

        this.plugin.messages().send(player, "command-shop-usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(final @NotNull CommandSender sender, final @NotNull Command command,
                                      final @NotNull String label, final String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("categorias", "categoria"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("categoria") || args[0].equalsIgnoreCase("category"))) {
            final List<String> ids = new ArrayList<>();
            this.plugin.catalog().categories().forEach(category -> ids.add(category.id()));
            return filter(ids, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(final List<String> options, final String prefix) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        final List<String> result = new ArrayList<>();
        for (final String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
