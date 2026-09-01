package dev.singlehope.free.shpix.payment;

import dev.singlehope.free.shpix.payment.gateway.GatewayType;
import dev.singlehope.free.shpix.util.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Order(long id,
                    String referenceId,
                    UUID payerId,
                    String payerName,
                    String productId,
                    GatewayType gateway,
                    String paymentId,
                    OrderStatus status,
                    BigDecimal amount,
                    String coupon,
                    String pixCode,
                    String ticketUrl,
                    Instant createdAt,
                    Instant expiresAt,
                    Instant updatedAt) {

    public Order {
        amount = Money.normalize(amount);
    }

    public static Order create(final String referenceId, final UUID payerId, final String payerName,
                               final String productId, final GatewayType gateway, final BigDecimal amount,
                               final String coupon, final Instant createdAt, final Instant expiresAt) {
        return new Order(0L, referenceId, payerId, payerName, productId, gateway, null,
                OrderStatus.WAITING, amount, coupon, null, null, createdAt, expiresAt, createdAt);
    }

    public Order withId(final long newId) {
        return new Order(newId, this.referenceId, this.payerId, this.payerName, this.productId, this.gateway,
                this.paymentId, this.status, this.amount, this.coupon, this.pixCode, this.ticketUrl,
                this.createdAt, this.expiresAt, this.updatedAt);
    }

    public Order withCharge(final String newPaymentId, final String newPixCode, final String newTicketUrl,
                            final Instant now) {
        return new Order(this.id, this.referenceId, this.payerId, this.payerName, this.productId, this.gateway,
                newPaymentId, this.status, this.amount, this.coupon, newPixCode, newTicketUrl,
                this.createdAt, this.expiresAt, now);
    }

    public Order withStatus(final OrderStatus newStatus, final Instant now) {
        return new Order(this.id, this.referenceId, this.payerId, this.payerName, this.productId, this.gateway,
                this.paymentId, newStatus, this.amount, this.coupon, this.pixCode, this.ticketUrl,
                this.createdAt, this.expiresAt, now);
    }

    public boolean isExpired(final Instant now) {
        return now.isAfter(this.expiresAt);
    }

    public String shortReference() {
        return this.referenceId.length() <= 8 ? this.referenceId : this.referenceId.substring(0, 8);
    }
}
