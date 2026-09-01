package dev.singlehope.free.shpix.shop;

import dev.singlehope.free.shpix.payment.gateway.GatewayType;
import dev.singlehope.free.shpix.shop.action.ActionType;
import dev.singlehope.free.shpix.shop.action.ProductAction;
import dev.singlehope.free.shpix.util.Money;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record Product(String id,
                      String name,
                      String categoryId,
                      ProductIcon icon,
                      Rewards rewards,
                      Map<ActionType, ProductAction> actions,
                      Set<GatewayType> gateways,
                      BigDecimal price) {

    public Product {
        actions = Map.copyOf(actions);
        gateways = Set.copyOf(gateways);
        price = Money.normalize(price);
    }

    public Optional<ProductAction> action(final ActionType type) {
        return Optional.ofNullable(this.actions.get(type));
    }

    public boolean supports(final GatewayType gateway) {
        return this.gateways.isEmpty() || this.gateways.contains(gateway);
    }
}
