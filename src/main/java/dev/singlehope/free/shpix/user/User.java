package dev.singlehope.free.shpix.user;

import dev.singlehope.free.shpix.util.Money;

import java.math.BigDecimal;
import java.util.UUID;

public record User(UUID uniqueId,
                   String name,
                   int totalOrders,
                   BigDecimal totalPaid,
                   BigDecimal totalRefunded,
                   BigDecimal balance) {

    public User {
        totalPaid = Money.normalize(totalPaid);
        totalRefunded = Money.normalize(totalRefunded);
        balance = Money.normalize(balance);
    }

    public static User empty(final UUID uniqueId, final String name) {
        return new User(uniqueId, name, 0, Money.ZERO, Money.ZERO, Money.ZERO);
    }
}
