package dev.singlehope.free.shpix.coupon;

import dev.singlehope.free.shpix.ShPixPlugin;
import dev.singlehope.free.shpix.menu.CheckoutMenu;
import dev.singlehope.free.shpix.shop.Product;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.Optional;

@SuppressWarnings("deprecation")
public final class CouponChatListener implements Listener {

    private final ShPixPlugin plugin;

    public CouponChatListener(final ShPixPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final Optional<String> pending = this.plugin.couponInput().consume(player.getUniqueId());
        if (pending.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        final Optional<Product> product = this.plugin.catalog().product(pending.get());
        if (product.isEmpty()) {
            this.plugin.messages().send(player, "coupon-cancelled");
            return;
        }

        final String input = event.getMessage().trim();
        if (input.isEmpty() || input.equalsIgnoreCase("cancelar") || input.equalsIgnoreCase("cancel")) {
            this.plugin.messages().send(player, "coupon-cancelled");
            reopen(player, product.get(), null);
            return;
        }

        final Optional<Coupon> coupon = this.plugin.coupons().find(input);
        if (coupon.isEmpty()) {
            this.plugin.messages().send(player, "coupon-invalid");
            reopen(player, product.get(), null);
            return;
        }

        this.plugin.messages().send(player, "coupon-applied", Map.of(
                "{coupon}", coupon.get().name(),
                "{percent}", coupon.get().percent().toPlainString()));
        reopen(player, product.get(), coupon.get());
    }

    private void reopen(final Player player, final Product product, final Coupon coupon) {
        new CheckoutMenu(this.plugin, product, coupon).open(this.plugin, player);
    }
}
