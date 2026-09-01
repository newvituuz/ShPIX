package dev.singlehope.free.shpix.http;

public final class RateLimiter {

    private final int capacity;
    private final double refillPerMillis;

    private double tokens;
    private long lastRefill;

    public RateLimiter(final int permitsPerMinute) {
        this.capacity = Math.max(1, permitsPerMinute);
        this.refillPerMillis = this.capacity / 60_000.0D;
        this.tokens = this.capacity;
        this.lastRefill = System.nanoTime() / 1_000_000L;
    }

    public synchronized boolean tryAcquire() {
        final long now = System.nanoTime() / 1_000_000L;
        final long elapsed = Math.max(0L, now - this.lastRefill);
        this.lastRefill = now;
        this.tokens = Math.min(this.capacity, this.tokens + elapsed * this.refillPerMillis);
        if (this.tokens < 1.0D) {
            return false;
        }
        this.tokens -= 1.0D;
        return true;
    }
}
