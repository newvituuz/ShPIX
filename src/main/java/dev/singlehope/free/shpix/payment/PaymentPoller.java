package dev.singlehope.free.shpix.payment;

import dev.singlehope.free.shpix.coupon.CouponInputService;
import dev.singlehope.free.shpix.scheduler.Schedulers;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.logging.Level;

public final class PaymentPoller {

    private final Plugin plugin;
    private final OrderService orders;
    private final CouponInputService couponInput;

    private Schedulers.Task task;

    public PaymentPoller(final Plugin plugin, final OrderService orders, final CouponInputService couponInput) {
        this.plugin = plugin;
        this.orders = orders;
        this.couponInput = couponInput;
    }

    public synchronized void start(final Duration interval) {
        stop();
        this.task = Schedulers.asyncTimer(this.plugin, this::tick, interval.toMillis(), interval.toMillis());
    }

    private void tick() {
        try {
            this.couponInput.purgeExpired();
            this.orders.poll();
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.WARNING, "Falha no ciclo de verificação de pagamentos.", exception);
        }
    }

    public synchronized void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }
}
