package dev.singlehope.free.shpix.command;

import dev.singlehope.free.shpix.ShPixPlugin;
import dev.singlehope.free.shpix.coupon.Coupon;
import dev.singlehope.free.shpix.payment.Order;
import dev.singlehope.free.shpix.payment.OrderStatus;
import dev.singlehope.free.shpix.scheduler.Schedulers;
import dev.singlehope.free.shpix.util.Money;
import dev.singlehope.free.shpix.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ShPixCommand implements TabExecutor {

    private static final String PERMISSION = "shpix.admin";

    private final ShPixPlugin plugin;

    public ShPixCommand(final ShPixPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender, final @NotNull Command command,
                             final @NotNull String label, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            this.plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            case "cupom", "coupon" -> coupon(sender, args);
            case "pedido", "order" -> order(sender, args);
            case "reentregar", "redeliver" -> redeliver(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void usage(final CommandSender sender) {
        sender.sendMessage(Text.color("&e/shpix reload &7- recarrega configurações, produtos e cupons."));
        sender.sendMessage(Text.color("&e/shpix status &7- estado do banco, gateways e pedidos em aberto."));
        sender.sendMessage(Text.color("&e/shpix cupom criar <nome> <porcentagem>"));
        sender.sendMessage(Text.color("&e/shpix cupom remover <nome>"));
        sender.sendMessage(Text.color("&e/shpix cupom listar"));
        sender.sendMessage(Text.color("&e/shpix pedido <referência>"));
        sender.sendMessage(Text.color("&e/shpix reentregar <referência>"));
    }

    private void reload(final CommandSender sender) {
        try {
            this.plugin.reloadEverything();
            this.plugin.messages().send(sender, "reload-success");
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Falha ao recarregar a configuração: " + exception.getClass().getSimpleName());
            this.plugin.messages().send(sender, "reload-failed");
        }
    }

    private void status(final CommandSender sender) {
        sender.sendMessage(Text.color("&8&m--------------------------------"));
        sender.sendMessage(Text.color("&fBanco de dados: " + (this.plugin.isStorageReady() ? "&aconectado" : "&cindisponível")));
        sender.sendMessage(Text.color("&fGateways ativas: &a" + this.plugin.gateways().available().size()));
        sender.sendMessage(Text.color("&fCategorias: &a" + this.plugin.catalog().categoryCount()
                + " &7| &fProdutos: &a" + this.plugin.catalog().productCount()));
        sender.sendMessage(Text.color("&fPedidos aguardando pagamento: &a" + this.plugin.orders().trackedCount()));
        sender.sendMessage(Text.color("&fScheduler: &a" + (Schedulers.isFolia() ? "Folia" : "Paper")));
        sender.sendMessage(Text.color("&8&m--------------------------------"));
    }

    private void coupon(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            usage(sender);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "criar", "create" -> createCoupon(sender, args);
            case "remover", "remove" -> removeCoupon(sender, args);
            case "listar", "list" -> listCoupons(sender);
            default -> usage(sender);
        }
    }

    private void createCoupon(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Text.color("&c/shpix cupom criar <nome> <porcentagem>"));
            return;
        }
        final String name = args[2];
        if (!this.plugin.coupons().isValidName(name)) {
            this.plugin.messages().send(sender, "coupon-invalid-name");
            return;
        }
        final BigDecimal percent = Money.of(args[3]);
        if (!this.plugin.coupons().isValidPercent(percent)) {
            this.plugin.messages().send(sender, "coupon-invalid-percent",
                    Map.of("{max}", this.plugin.coupons().maxPercent().toPlainString()));
            return;
        }
        if (!this.plugin.coupons().save(new Coupon(name, percent))) {
            this.plugin.messages().send(sender, "coupon-save-failed");
            return;
        }
        this.plugin.messages().send(sender, "coupon-created",
                Map.of("{coupon}", name, "{percent}", percent.toPlainString()));
    }

    private void removeCoupon(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Text.color("&c/shpix cupom remover <nome>"));
            return;
        }
        if (!this.plugin.coupons().remove(args[2])) {
            this.plugin.messages().send(sender, "coupon-not-found");
            return;
        }
        this.plugin.messages().send(sender, "coupon-removed", Map.of("{coupon}", args[2]));
    }

    private void listCoupons(final CommandSender sender) {
        final List<Coupon> coupons = this.plugin.coupons().all();
        if (coupons.isEmpty()) {
            this.plugin.messages().send(sender, "coupon-list-empty");
            return;
        }
        for (final Coupon coupon : coupons) {
            sender.sendMessage(Text.color("&8- &f" + coupon.name() + " &7(" + coupon.percent().toPlainString() + "%)"));
        }
    }

    private void order(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.color("&c/shpix pedido <referência>"));
            return;
        }
        final String reference = args[1];
        Schedulers.async(this.plugin, () -> {
            final Optional<Order> order = this.plugin.orders().findByReference(reference);
            if (order.isEmpty()) {
                this.plugin.messages().send(sender, "order-not-found");
                return;
            }
            final Order value = order.get();
            sender.sendMessage(Text.color("&fPedido &7" + value.shortReference()
                    + " &8| &fJogador: &7" + value.payerName()
                    + " &8| &fProduto: &7" + value.productId()));
            sender.sendMessage(Text.color("&fValor: &2R$&a" + Money.format(value.amount())
                    + " &8| &fEstado: &7" + value.status().name()));
        });
    }

    private void redeliver(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.color("&c/shpix reentregar <referência>"));
            return;
        }
        final String reference = args[1];
        Schedulers.async(this.plugin, () -> {
            final Optional<Order> order = this.plugin.orders().findByReference(reference);
            if (order.isEmpty()) {
                this.plugin.messages().send(sender, "order-not-found");
                return;
            }
            final Order value = order.get();
            if (value.status() != OrderStatus.PAID) {
                this.plugin.messages().send(sender, "order-not-redeliverable",
                        Map.of("{status}", value.status().name()));
                return;
            }
            this.plugin.orders().attemptDelivery(value);
            this.plugin.messages().send(sender, "order-redelivery-scheduled",
                    Map.of("{reference}", value.shortReference()));
        });
    }

    @Override
    public List<String> onTabComplete(final @NotNull CommandSender sender, final @NotNull Command command,
                                      final @NotNull String label, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("reload", "status", "cupom", "pedido", "reentregar"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("cupom") || args[0].equalsIgnoreCase("coupon"))) {
            return filter(List.of("criar", "remover", "listar"), args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remover")) {
            final List<String> names = new ArrayList<>();
            this.plugin.coupons().all().forEach(coupon -> names.add(coupon.name()));
            return filter(names, args[2]);
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
