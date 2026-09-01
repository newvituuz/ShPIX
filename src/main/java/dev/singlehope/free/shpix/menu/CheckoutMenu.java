package dev.singlehope.free.shpix.menu;

import dev.singlehope.free.shpix.ShPixPlugin;
import dev.singlehope.free.shpix.coupon.Coupon;
import dev.singlehope.free.shpix.payment.OrderItems;
import dev.singlehope.free.shpix.payment.PriceQuote;
import dev.singlehope.free.shpix.payment.gateway.GatewayType;
import dev.singlehope.free.shpix.shop.Product;
import dev.singlehope.free.shpix.util.ItemBuilder;
import dev.singlehope.free.shpix.util.Money;
import dev.singlehope.free.shpix.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CheckoutMenu extends Menu {

    private static final String MERCADO_PAGO_TEXTURE =
            "c0c9a3a4ffbee61d1ee1c3a533355bda9cdc377e07b0ff8bc618d3977b7f86cc";
    private static final int[] GATEWAY_SLOTS = {11, 12, 13, 14, 15};

    private final ShPixPlugin plugin;
    private final Product product;
    private final Coupon coupon;
    private final AtomicBoolean submitted = new AtomicBoolean();

    public CheckoutMenu(final ShPixPlugin plugin, final Product product, final Coupon coupon) {
        super(Text.color("&8Pagamento - " + product.name()), 3);
        this.plugin = plugin;
        this.product = product;
        this.coupon = coupon;
    }

    @Override
    protected void build(final Player viewer) {
        final PriceQuote quote = PriceQuote.of(this.product, this.coupon, this.plugin.pluginConfig());
        final List<GatewayType> available = new ArrayList<>();
        for (final GatewayType type : this.plugin.gateways().available()) {
            if (this.product.supports(type)) {
                available.add(type);
            }
        }

        if (available.isEmpty()) {
            set(13, new ItemBuilder(Material.BARRIER)
                    .name("&cNenhum método de pagamento disponível.")
                    .build());
        }

        for (int index = 0; index < available.size() && index < GATEWAY_SLOTS.length; index++) {
            final GatewayType type = available.get(index);
            set(GATEWAY_SLOTS[index], gatewayIcon(type, quote), event -> submit(viewer, type));
        }

        set(20, new ItemBuilder(Material.NAME_TAG)
                .name("&aAdicionar cupom")
                .legacyLore(List.of(
                        "&7Possui um cupom de desconto?",
                        "&7Clique aqui para informá-lo no chat.",
                        "",
                        this.coupon == null
                                ? "&eNenhum cupom ativo."
                                : "&aCupom ativo: &f" + this.coupon.name() + " &7(" + this.coupon.percent().toPlainString() + "%)"))
                .build(),
                event -> requestCoupon(viewer));

        set(24, new ItemBuilder(Material.ARROW)
                .name("&cVoltar")
                .build(),
                event -> this.plugin.catalog().category(this.product.categoryId())
                        .ifPresentOrElse(
                                category -> new ProductsMenu(this.plugin, category).open(this.plugin, viewer),
                                viewer::closeInventory));
    }

    private org.bukkit.inventory.ItemStack gatewayIcon(final GatewayType type, final PriceQuote quote) {
        final List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("  &8&l> &fPreço do produto: &2R$&a" + Money.format(quote.base()));
        if (quote.discount().signum() > 0) {
            lore.add("  &8&l> &fDesconto: &c-R$&a" + Money.format(quote.discount()));
        }
        lore.add("  &8&l> &fTaxa de pagamento: &2R$&a" + Money.format(quote.fee()));
        lore.add("");
        lore.add("  &8&l> &fTotal: &2R$&a" + Money.format(quote.total()));
        lore.add("");
        lore.add("&eClique aqui para gerar o PIX.");

        final ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                .name("&aPague com &b" + type.displayName() + "&a!")
                .legacyLore(lore);
        if (type == GatewayType.MERCADO_PAGO) {
            builder.texture(MERCADO_PAGO_TEXTURE);
        }
        return builder.build();
    }

    private void requestCoupon(final Player viewer) {
        viewer.closeInventory();
        this.plugin.couponInput().await(viewer.getUniqueId(), this.product.id());
        this.plugin.messages().send(viewer, "coupon-prompt");
    }

    private void submit(final Player viewer, final GatewayType gateway) {
        if (!this.submitted.compareAndSet(false, true)) {
            return;
        }
        viewer.closeInventory();

        if (!this.plugin.isStorageReady()) {
            this.plugin.messages().send(viewer, "payment-database-unavailable");
            this.submitted.set(false);
            return;
        }
        if (this.plugin.orders().hasActiveOrder(viewer.getUniqueId())) {
            this.plugin.messages().send(viewer, "already-have-order");
            this.submitted.set(false);
            return;
        }
        if (OrderItems.findFreeSlot(viewer) < 0) {
            this.plugin.messages().send(viewer, "inventory-full");
            this.submitted.set(false);
            return;
        }
        if (this.plugin.pluginConfig().requireEmptyInventoryForItems()
                && this.product.rewards().hasItems()
                && !hasRoomForRewards(viewer)) {
            this.plugin.messages().send(viewer, "must-inventory-clean");
            this.submitted.set(false);
            return;
        }

        final LoadingMenu loading = new LoadingMenu(this.plugin, viewer, Text.color("&8Gerando QR Code..."));
        loading.start();

        this.plugin.orders().createOrder(viewer, this.product, this.coupon, gateway, result -> {
            if (!result.success()) {
                loading.stop();
                this.submitted.set(false);
                this.plugin.messages().send(viewer, result.messageKey());
                return;
            }
            this.plugin.qrCodes().deliverQrCode(viewer, result.order(), delivered -> {
                loading.stop();
                if (!delivered) {
                    this.plugin.messages().send(viewer, "qrcode-delivery-failed",
                            Map.of("{reference}", result.order().shortReference()));
                }
            });
        });
    }

    private boolean hasRoomForRewards(final Player viewer) {
        int free = 0;
        for (int slot = 0; slot < 36; slot++) {
            final var stack = viewer.getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                free++;
            }
        }
        return free > this.product.rewards().items().size();
    }
}
