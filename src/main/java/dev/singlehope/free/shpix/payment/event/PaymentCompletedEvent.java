package dev.singlehope.free.shpix.payment.event;

import dev.singlehope.free.shpix.payment.Order;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PaymentCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Order order;

    public PaymentCompletedEvent(final Player player, final Order order) {
        this.player = player;
        this.order = order;
    }

    public Player getPlayer() {
        return this.player;
    }

    public Order getOrder() {
        return this.order;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
