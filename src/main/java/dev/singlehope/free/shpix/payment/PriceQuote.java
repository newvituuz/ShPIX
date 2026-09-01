package dev.singlehope.free.shpix.payment;

import dev.singlehope.free.shpix.config.PluginConfig;
import dev.singlehope.free.shpix.coupon.Coupon;
import dev.singlehope.free.shpix.shop.Product;
import dev.singlehope.free.shpix.util.Money;

import java.math.BigDecimal;

public record PriceQuote(BigDecimal base, BigDecimal discount, BigDecimal net, BigDecimal fee, BigDecimal total) {

    public static PriceQuote of(final Product product, final Coupon coupon, final PluginConfig config) {
        final BigDecimal base = Money.normalize(product.price());
        BigDecimal discount = Money.ZERO;
        if (coupon != null) {
            final BigDecimal percent = coupon.percent().min(config.maxCouponPercent()).max(BigDecimal.ZERO);
            discount = Money.percentOf(base, percent);
        }
        if (discount.compareTo(base) > 0) {
            discount = base;
        }
        final BigDecimal net = Money.normalize(base.subtract(discount));
        final BigDecimal fee = Money.percentOf(net, config.feePercent());
        final BigDecimal total = Money.normalize(net.add(fee));
        return new PriceQuote(base, discount, net, fee, total);
    }

    public boolean isChargeable(final PluginConfig config) {
        return Money.isPositive(this.total) && Money.isWithin(this.total, config.minAmount(), config.maxAmount());
    }
}
