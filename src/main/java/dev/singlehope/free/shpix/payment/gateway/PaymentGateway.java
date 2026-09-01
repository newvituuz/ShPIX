package dev.singlehope.free.shpix.payment.gateway;

import dev.singlehope.free.shpix.payment.Order;

public interface PaymentGateway extends AutoCloseable {

    boolean isConfigured();

    Charge createCharge(Order order) throws GatewayException;

    PaymentState queryState(Order order) throws GatewayException;

    @Override
    void close();
}
