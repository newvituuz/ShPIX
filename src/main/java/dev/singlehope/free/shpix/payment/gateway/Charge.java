package dev.singlehope.free.shpix.payment.gateway;

public record Charge(String paymentId, String pixCode, String ticketUrl) {
}
