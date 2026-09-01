package dev.singlehope.free.shpix.payment;

import dev.singlehope.free.shpix.config.PluginConfig;
import dev.singlehope.free.shpix.coupon.Coupon;
import dev.singlehope.free.shpix.payment.gateway.Charge;
import dev.singlehope.free.shpix.payment.gateway.GatewayException;
import dev.singlehope.free.shpix.payment.gateway.GatewayRegistry;
import dev.singlehope.free.shpix.payment.gateway.GatewayType;
import dev.singlehope.free.shpix.payment.gateway.PaymentGateway;
import dev.singlehope.free.shpix.payment.gateway.PaymentState;
import dev.singlehope.free.shpix.scheduler.Schedulers;
import dev.singlehope.free.shpix.shop.Product;
import dev.singlehope.free.shpix.storage.Database;
import dev.singlehope.free.shpix.storage.OrderRepository;
import dev.singlehope.free.shpix.storage.UserRepository;
import dev.singlehope.free.shpix.user.UserService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final Plugin plugin;
    private final Logger logger;
    private final Database database;
    private final OrderRepository orders;
    private final UserRepository users;
    private final GatewayRegistry gateways;
    private final RewardService rewards;
    private final UserService userService;

    private final Map<String, Tracked> tracked = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeByPlayer = new ConcurrentHashMap<>();

    private volatile PluginConfig config;
    private volatile BiConsumer<Player, Order> waitingHandler;

    public OrderService(final Plugin plugin, final PluginConfig config, final Database database,
                        final OrderRepository orders, final UserRepository users,
                        final GatewayRegistry gateways, final RewardService rewards, final UserService userService) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = config;
        this.database = database;
        this.orders = orders;
        this.users = users;
        this.gateways = gateways;
        this.rewards = rewards;
        this.userService = userService;
    }

    public void updateConfig(final PluginConfig newConfig) {
        this.config = newConfig;
    }

    public boolean hasActiveOrder(final UUID playerId) {
        return this.activeByPlayer.containsKey(playerId);
    }

    public int trackedCount() {
        return this.tracked.size();
    }

    public void loadPending() {
        if (!this.database.isAvailable()) {
            return;
        }
        try {
            final List<Order> pending = this.orders.findByStatus(List.of(OrderStatus.WAITING));
            for (final Order order : pending) {
                track(order);
            }
            if (!pending.isEmpty()) {
                this.logger.info("Recuperados " + pending.size() + " pagamentos pendentes.");
            }
        } catch (SQLException exception) {
            this.logger.warning("Não foi possível recuperar os pagamentos pendentes do banco de dados.");
        }
    }

    private void track(final Order order) {
        track(order, true);
    }

    private void track(final Order order, final boolean persisted) {
        final Tracked entry = new Tracked(order);
        entry.pendingPersist = !persisted;
        this.tracked.put(order.referenceId(), entry);
        this.activeByPlayer.put(order.payerId(), order.referenceId());
    }

    private void untrack(final Order order) {
        this.tracked.remove(order.referenceId());
        this.activeByPlayer.remove(order.payerId(), order.referenceId());
    }

    public void createOrder(final Player player, final Product product, final Coupon coupon,
                            final GatewayType gatewayType, final Consumer<CreationResult> callback) {
        final UUID playerId = player.getUniqueId();
        final String playerName = player.getName();

        if (!this.database.isAvailable()) {
            complete(player, callback, CreationResult.failure("payment-database-unavailable"));
            return;
        }

        final Optional<PaymentGateway> gatewayOptional = this.gateways.gateway(gatewayType);
        if (gatewayOptional.isEmpty()) {
            complete(player, callback, CreationResult.failure("payment-gateway-unavailable"));
            return;
        }
        if (!product.supports(gatewayType)) {
            complete(player, callback, CreationResult.failure("payment-gateway-unavailable"));
            return;
        }

        final PriceQuote quote = PriceQuote.of(product, coupon, this.config);
        if (!quote.isChargeable(this.config)) {
            complete(player, callback, CreationResult.failure("payment-amount-invalid"));
            return;
        }

        final String referenceId = newReference();
        if (this.activeByPlayer.putIfAbsent(playerId, referenceId) != null) {
            complete(player, callback, CreationResult.failure("already-have-order"));
            return;
        }

        final Instant now = Instant.now();
        final Order draft = Order.create(referenceId, playerId, playerName, product.id(), gatewayType,
                quote.total(), coupon == null ? null : coupon.name(), now, now.plus(this.config.orderExpiration()));

        Schedulers.async(this.plugin, () -> {
            Order stored;
            try {
                stored = this.orders.insert(draft);
            } catch (SQLException exception) {
                this.activeByPlayer.remove(playerId, referenceId);
                this.logger.warning("Não foi possível registrar o pedido no banco de dados.");
                complete(player, callback, CreationResult.failure("payment-database-unavailable"));
                return;
            }

            final Charge charge;
            try {
                charge = gatewayOptional.get().createCharge(stored);
            } catch (GatewayException exception) {
                this.activeByPlayer.remove(playerId, referenceId);
                safeTransition(referenceId, OrderStatus.WAITING, OrderStatus.CANCELLED);
                this.logger.warning("Falha ao criar cobrança " + stored.shortReference() + ": " + exception.getMessage());
                complete(player, callback, CreationResult.failure("payment-create-failed"));
                return;
            }

            final Order charged = stored.withCharge(charge.paymentId(), charge.pixCode(), charge.ticketUrl(), Instant.now());
            boolean persisted = true;
            try {
                this.orders.updateCharge(charged);
            } catch (SQLException exception) {
                persisted = false;
                this.logger.log(Level.WARNING, "Cobrança " + charged.shortReference()
                        + " criada mas não persistida; será reconciliada no próximo ciclo.");
            }

            track(charged, persisted);
            incrementOrders(playerId);
            complete(player, callback, CreationResult.success(charged));
        });
    }

    private void complete(final Player player, final Consumer<CreationResult> callback, final CreationResult result) {
        Schedulers.entity(this.plugin, player, () -> callback.accept(result));
    }

    private void incrementOrders(final UUID playerId) {
        try {
            this.users.incrementOrders(playerId);
            this.userService.refresh(playerId);
        } catch (SQLException ignored) {
            // estatística não crítica
        }
    }

    public void poll() {
        if (this.tracked.isEmpty()) {
            return;
        }
        final Instant now = Instant.now();
        final AtomicInteger checked = new AtomicInteger();
        for (final Tracked entry : this.tracked.values()) {
            if (checked.get() >= 50) {
                break;
            }
            final Order order = entry.order();
            if (order.status() != OrderStatus.WAITING) {
                untrack(order);
                continue;
            }
            if (order.paymentId() == null) {
                if (order.isExpired(now)) {
                    expire(order);
                }
                continue;
            }
            if (entry.pendingPersist) {
                reconcileCharge(entry);
            }
            if (now.isBefore(entry.nextCheck())) {
                continue;
            }
            checked.incrementAndGet();
            checkOrder(entry, now);
        }
    }

    private void reconcileCharge(final Tracked entry) {
        try {
            this.orders.updateCharge(entry.order());
            entry.pendingPersist = false;
        } catch (SQLException ignored) {
            // nova tentativa no próximo ciclo
        }
    }

    private void checkOrder(final Tracked entry, final Instant now) {
        final Order order = entry.order();
        final Optional<PaymentGateway> gateway = this.gateways.gateway(order.gateway());
        if (gateway.isEmpty()) {
            entry.delay(this.config.pollInterval());
            return;
        }

        PaymentState state;
        try {
            state = gateway.get().queryState(order);
            entry.resetFailures(this.config.pollInterval());
        } catch (GatewayException exception) {
            entry.backoff(this.config.pollInterval(), MAX_BACKOFF);
            if (!exception.isRetryable()) {
                this.logger.warning("Consulta do pedido " + order.shortReference() + " falhou: " + exception.getMessage());
            }
            return;
        }

        switch (state) {
            case APPROVED -> approve(order);
            case CANCELLED -> finish(order, OrderStatus.CANCELLED);
            case REFUNDED -> finish(order, OrderStatus.REFUNDED);
            case PENDING, UNKNOWN -> {
                if (order.isExpired(now)) {
                    expire(order);
                }
            }
        }
    }

    private void approve(final Order order) {
        if (!safeTransition(order.referenceId(), OrderStatus.WAITING, OrderStatus.PAID)) {
            untrack(order);
            return;
        }
        final Order paid = order.withStatus(OrderStatus.PAID, Instant.now());
        untrack(paid);
        addPaid(paid);
        this.logger.info("Pagamento confirmado: pedido " + paid.shortReference() + " de " + paid.payerName() + ".");
        attemptDelivery(paid);
    }

    private void addPaid(final Order order) {
        try {
            this.users.addPaid(order.payerId(), order.amount());
            this.userService.refresh(order.payerId());
        } catch (SQLException ignored) {
            // estatística não crítica
        }
    }

    public void attemptDelivery(final Order order) {
        final Player player = this.plugin.getServer().getPlayer(order.payerId());
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!safeTransition(order.referenceId(), OrderStatus.PAID, OrderStatus.DELIVERED)) {
            return;
        }
        final Order delivered = order.withStatus(OrderStatus.DELIVERED, Instant.now());
        Schedulers.entity(this.plugin, player, () -> {
            if (!player.isOnline()) {
                releaseDelivery(delivered);
                return;
            }
            try {
                this.rewards.deliver(player, delivered);
            } catch (Exception exception) {
                this.logger.log(Level.SEVERE, "Falha ao entregar o pedido " + delivered.shortReference()
                        + "; ele foi devolvido para entrega posterior.", exception);
                releaseDelivery(delivered);
            }
        }, () -> releaseDelivery(delivered));
    }

    public void setWaitingHandler(final BiConsumer<Player, Order> handler) {
        this.waitingHandler = handler;
    }

    public void deliverPendingFor(final Player player) {
        if (!this.database.isAvailable()) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        Schedulers.async(this.plugin, () -> {
            final Set<String> open = new HashSet<>();
            try {
                for (final Order order : this.orders.findByPayerAndStatus(playerId, OrderStatus.PAID)) {
                    attemptDelivery(order);
                }
                for (final Order order : this.orders.findByPayerAndStatus(playerId, OrderStatus.WAITING)) {
                    open.add(order.referenceId());
                    this.tracked.putIfAbsent(order.referenceId(), new Tracked(order));
                    this.activeByPlayer.putIfAbsent(playerId, order.referenceId());
                    final BiConsumer<Player, Order> handler = this.waitingHandler;
                    if (handler != null && order.pixCode() != null) {
                        handler.accept(player, order);
                    }
                }
            } catch (SQLException exception) {
                return;
            }
            Schedulers.entity(this.plugin, player, () -> {
                if (player.isOnline()) {
                    OrderItems.removeStale(player, open);
                }
            });
        });
    }

    private void expire(final Order order) {
        if (!safeTransition(order.referenceId(), OrderStatus.WAITING, OrderStatus.EXPIRED)) {
            untrack(order);
            return;
        }
        finishLocally(order.withStatus(OrderStatus.EXPIRED, Instant.now()));
    }

    private void finish(final Order order, final OrderStatus status) {
        if (!safeTransition(order.referenceId(), OrderStatus.WAITING, status)) {
            untrack(order);
            return;
        }
        finishLocally(order.withStatus(status, Instant.now()));
    }

    private void finishLocally(final Order order) {
        untrack(order);
        final Player player = this.plugin.getServer().getPlayer(order.payerId());
        if (player == null || !player.isOnline()) {
            return;
        }
        Schedulers.entity(this.plugin, player, () -> this.rewards.notifyClosed(player, order));
    }

    private void releaseDelivery(final Order order) {
        Schedulers.async(this.plugin, () -> safeTransition(order.referenceId(), OrderStatus.DELIVERED, OrderStatus.PAID));
    }

    private boolean safeTransition(final String referenceId, final OrderStatus from, final OrderStatus to) {
        try {
            return this.orders.transition(referenceId, from, to, Instant.now());
        } catch (SQLException exception) {
            this.logger.warning("Não foi possível atualizar o estado do pedido " + referenceId.substring(0, 8) + ".");
            return false;
        }
    }

    public Optional<Order> findByReference(final String referenceId) {
        try {
            return this.orders.findByReference(referenceId);
        } catch (SQLException exception) {
            return Optional.empty();
        }
    }

    public List<Order> recentOrders(final UUID playerId, final int limit) {
        try {
            return this.orders.findRecentByPayer(playerId, limit);
        } catch (SQLException exception) {
            return List.of();
        }
    }

    public void shutdown() {
        this.tracked.clear();
        this.activeByPlayer.clear();
    }

    private static String newReference() {
        final byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private static final class Tracked {

        private volatile Order order;
        private volatile Instant nextCheck = Instant.EPOCH;
        private volatile int failures;
        private volatile boolean pendingPersist;

        private Tracked(final Order order) {
            this.order = order;
        }

        private Order order() {
            return this.order;
        }

        private Instant nextCheck() {
            return this.nextCheck;
        }

        private void delay(final Duration interval) {
            this.nextCheck = Instant.now().plus(interval);
        }

        private void resetFailures(final Duration interval) {
            this.failures = 0;
            this.nextCheck = Instant.now().plus(interval);
        }

        private void backoff(final Duration interval, final Duration max) {
            this.failures = Math.min(8, this.failures + 1);
            final long millis = Math.min(max.toMillis(), interval.toMillis() * (1L << this.failures));
            this.nextCheck = Instant.now().plusMillis(millis);
        }
    }

    public record CreationResult(boolean success, Order order, String messageKey) {

        public static CreationResult success(final Order order) {
            return new CreationResult(true, order, null);
        }

        public static CreationResult failure(final String messageKey) {
            return new CreationResult(false, null, messageKey);
        }
    }
}
