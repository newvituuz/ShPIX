package dev.singlehope.free.shpix.payment.gateway.mercadopago;

import com.google.gson.JsonObject;
import dev.singlehope.free.shpix.http.HttpException;
import dev.singlehope.free.shpix.http.JsonHttpClient;
import dev.singlehope.free.shpix.payment.Order;
import dev.singlehope.free.shpix.payment.gateway.Charge;
import dev.singlehope.free.shpix.payment.gateway.GatewayException;
import dev.singlehope.free.shpix.payment.gateway.PaymentGateway;
import dev.singlehope.free.shpix.payment.gateway.PaymentState;
import dev.singlehope.free.shpix.util.Money;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class MercadoPagoGateway implements PaymentGateway {

    private static final String BASE_URL = "https://api.mercadopago.com/v1/payments";
    private static final Set<String> ALLOWED_HOSTS = Set.of("api.mercadopago.com");
    private static final Set<String> TICKET_HOSTS = Set.of(
            "www.mercadopago.com.br", "www.mercadopago.com", "mercadopago.com.br", "mercadopago.com");
    private static final Duration MIN_REMOTE_EXPIRATION = Duration.ofMinutes(30);
    private static final DateTimeFormatter EXPIRATION_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);

    private final Logger logger;
    private final String accessToken;
    private final String payerEmailDomain;
    private final JsonHttpClient http;

    public MercadoPagoGateway(final Logger logger, final String accessToken, final String payerEmailDomain,
                              final Duration timeout, final int requestsPerMinute) {
        this.logger = logger;
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.payerEmailDomain = payerEmailDomain;
        this.http = new JsonHttpClient("ShPIX-MercadoPago", timeout, requestsPerMinute, ALLOWED_HOSTS);
    }

    @Override
    public boolean isConfigured() {
        return !this.accessToken.isEmpty() && !"token".equalsIgnoreCase(this.accessToken);
    }

    @Override
    public Charge createCharge(final Order order) throws GatewayException {
        requireConfigured();

        final JsonObject payer = new JsonObject();
        payer.addProperty("email", order.referenceId().toLowerCase(Locale.ROOT) + "@" + this.payerEmailDomain);

        final JsonObject body = new JsonObject();
        body.addProperty("transaction_amount", Money.normalize(order.amount()));
        body.addProperty("description", "ShPIX #" + order.shortReference());
        body.addProperty("payment_method_id", "pix");
        body.addProperty("external_reference", order.referenceId());
        if (Duration.between(Instant.now(), order.expiresAt()).compareTo(MIN_REMOTE_EXPIRATION) >= 0) {
            body.addProperty("date_of_expiration", OffsetDateTime.ofInstant(order.expiresAt(), ZoneId.systemDefault())
                    .format(EXPIRATION_FORMAT));
        }
        body.add("payer", payer);

        final Map<String, String> headers = authHeaders();
        headers.put("X-Idempotency-Key", order.referenceId());

        final JsonHttpClient.JsonResponse response = execute(() -> this.http.post(URI.create(BASE_URL), body.toString(), headers));
        if (!response.isSuccessful()) {
            throw failure("criar a cobrança", response.status());
        }

        final JsonObject json = response.body();
        final String paymentId = string(json, "id");
        final JsonObject interaction = json.getAsJsonObject("point_of_interaction");
        final JsonObject transaction = interaction == null ? null : interaction.getAsJsonObject("transaction_data");
        if (paymentId == null || transaction == null) {
            throw new GatewayException("Resposta inesperada do Mercado Pago ao criar a cobrança.", true);
        }

        final String pixCode = string(transaction, "qr_code");
        if (pixCode == null || pixCode.isBlank()) {
            throw new GatewayException("O Mercado Pago não retornou o código PIX.", true);
        }

        if (!matchesAmount(json, order.amount())) {
            throw new GatewayException("O valor confirmado pelo Mercado Pago não corresponde ao pedido.", false);
        }

        final String ticketUrl = sanitizeTicketUrl(string(transaction, "ticket_url"));
        return new Charge(paymentId, pixCode, ticketUrl);
    }

    @Override
    public PaymentState queryState(final Order order) throws GatewayException {
        requireConfigured();
        if (order.paymentId() == null || order.paymentId().isBlank()) {
            return PaymentState.UNKNOWN;
        }
        if (!order.paymentId().chars().allMatch(Character::isDigit)) {
            return PaymentState.UNKNOWN;
        }

        final URI uri = URI.create(BASE_URL + "/" + order.paymentId());
        final JsonHttpClient.JsonResponse response = execute(() -> this.http.get(uri, authHeaders()));
        if (response.status() == 404) {
            return PaymentState.UNKNOWN;
        }
        if (!response.isSuccessful()) {
            throw failure("consultar o pagamento", response.status());
        }

        final JsonObject json = response.body();
        final String reference = string(json, "external_reference");
        if (reference == null || !reference.equals(order.referenceId())) {
            this.logger.warning("Pagamento " + order.shortReference() + " ignorado: referência divergente.");
            return PaymentState.UNKNOWN;
        }

        final String status = string(json, "status");
        if (status == null) {
            return PaymentState.UNKNOWN;
        }

        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approved" -> matchesAmount(json, order.amount()) ? PaymentState.APPROVED : deny(order);
            case "pending", "in_process", "authorized", "in_mediation" -> PaymentState.PENDING;
            case "rejected", "cancelled" -> PaymentState.CANCELLED;
            case "refunded", "charged_back" -> PaymentState.REFUNDED;
            default -> PaymentState.UNKNOWN;
        };
    }

    private PaymentState deny(final Order order) {
        this.logger.warning("Pagamento " + order.shortReference() + " aprovado com valor divergente; entrega bloqueada.");
        return PaymentState.UNKNOWN;
    }

    private static boolean matchesAmount(final JsonObject json, final BigDecimal expected) {
        if (!json.has("transaction_amount") || json.get("transaction_amount").isJsonNull()) {
            return false;
        }
        try {
            final BigDecimal reported = Money.normalize(json.get("transaction_amount").getAsBigDecimal());
            return reported.compareTo(Money.normalize(expected)) == 0;
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException ignored) {
            return false;
        }
    }

    private Map<String, String> authHeaders() {
        final Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + this.accessToken);
        return headers;
    }

    private void requireConfigured() throws GatewayException {
        if (!isConfigured()) {
            throw new GatewayException("O access token do Mercado Pago não está configurado.", false);
        }
    }

    private GatewayException failure(final String action, final int status) {
        final boolean retryable = status == 429 || status >= 500;
        if (status == 401 || status == 403) {
            this.logger.severe("O Mercado Pago recusou as credenciais configuradas (HTTP " + status + ").");
        } else {
            this.logger.warning("Falha ao " + action + " no Mercado Pago (HTTP " + status + ").");
        }
        return new GatewayException("O provedor de pagamento recusou a operação.", retryable);
    }

    private static String string(final JsonObject json, final String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        try {
            return json.get(key).getAsString();
        } catch (UnsupportedOperationException | IllegalStateException ignored) {
            return null;
        }
    }

    private static String sanitizeTicketUrl(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            final URI uri = URI.create(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                return null;
            }
            return TICKET_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT)) ? uri.toString() : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private JsonHttpClient.JsonResponse execute(final HttpCall call) throws GatewayException {
        try {
            return call.run();
        } catch (HttpException exception) {
            throw new GatewayException(exception.getMessage(), true);
        }
    }

    @Override
    public void close() {
        this.http.close();
    }

    @FunctionalInterface
    private interface HttpCall {

        JsonHttpClient.JsonResponse run() throws HttpException;
    }
}
