package dev.singlehope.free.shpix.payment.gateway;

import dev.singlehope.free.shpix.config.PluginConfig;
import dev.singlehope.free.shpix.payment.gateway.mercadopago.MercadoPagoGateway;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class GatewayRegistry implements AutoCloseable {

    private final Map<GatewayType, PaymentGateway> gateways = new EnumMap<>(GatewayType.class);
    private final Logger logger;

    public GatewayRegistry(final Logger logger) {
        this.logger = logger;
    }

    public void load(final PluginConfig config, final FileConfiguration raw) {
        close();
        for (final GatewayType type : GatewayType.values()) {
            final boolean enabled = raw.getBoolean("gateways." + type.name() + ".enabled", true);
            if (!enabled) {
                continue;
            }
            final String token = config.gatewayToken(raw, type.name());
            final PaymentGateway gateway = create(type, token, config);
            if (gateway == null) {
                continue;
            }
            if (!gateway.isConfigured()) {
                this.logger.warning("A gateway " + type.displayName() + " está sem credenciais válidas e ficará indisponível.");
                gateway.close();
                continue;
            }
            this.gateways.put(type, gateway);
            this.logger.info("Gateway " + type.displayName() + " carregada.");
        }
        if (this.gateways.isEmpty()) {
            this.logger.warning("Nenhuma gateway de pagamento configurada; as compras ficarão indisponíveis.");
        }
    }

    private PaymentGateway create(final GatewayType type, final String token, final PluginConfig config) {
        return switch (type) {
            case MERCADO_PAGO -> new MercadoPagoGateway(this.logger, token, config.payerEmailDomain(),
                    config.httpTimeout(), config.requestsPerMinute());
        };
    }

    public Optional<PaymentGateway> gateway(final GatewayType type) {
        return Optional.ofNullable(this.gateways.get(type));
    }

    public List<GatewayType> available() {
        return List.copyOf(this.gateways.keySet());
    }

    @Override
    public void close() {
        this.gateways.values().forEach(PaymentGateway::close);
        this.gateways.clear();
    }
}
