package dev.singlehope.free.shpix.payment.gateway;

public class GatewayException extends Exception {

    private final boolean retryable;

    public GatewayException(final String message, final boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return this.retryable;
    }
}
