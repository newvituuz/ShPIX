package dev.singlehope.free.shpix.coupon;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CouponInputService {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void await(final UUID playerId, final String productId) {
        this.pending.put(playerId, new Pending(productId, Instant.now().plus(TIMEOUT)));
    }

    public Optional<String> consume(final UUID playerId) {
        final Pending entry = this.pending.remove(playerId);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.productId());
    }

    public void cancel(final UUID playerId) {
        this.pending.remove(playerId);
    }

    public void purgeExpired() {
        final Instant now = Instant.now();
        this.pending.values().removeIf(entry -> now.isAfter(entry.expiresAt()));
    }

    public void clear() {
        this.pending.clear();
    }

    private record Pending(String productId, Instant expiresAt) {
    }
}
