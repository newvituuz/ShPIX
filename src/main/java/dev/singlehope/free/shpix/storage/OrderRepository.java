package dev.singlehope.free.shpix.storage;

import dev.singlehope.free.shpix.payment.Order;
import dev.singlehope.free.shpix.payment.OrderStatus;
import dev.singlehope.free.shpix.payment.gateway.GatewayType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

public final class OrderRepository {

    private static final String COLUMNS = "`id`, `reference_id`, `payer_id`, `payer_name`, `product_id`, `gateway`,"
            + " `payment_id`, `status`, `amount`, `coupon`, `pix_code`, `ticket_url`, `created_at`, `expires_at`, `updated_at`";

    private final Database database;
    private final String table;

    public OrderRepository(final Database database, final String prefix) {
        this.database = database;
        this.table = "`" + prefix + "orders`";
    }

    public Order insert(final Order order) throws SQLException {
        final String sql = "INSERT INTO " + this.table + " (`reference_id`, `payer_id`, `payer_name`, `product_id`,"
                + " `gateway`, `payment_id`, `status`, `amount`, `coupon`, `pix_code`, `ticket_url`,"
                + " `created_at`, `expires_at`, `updated_at`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, order.referenceId());
            statement.setString(2, order.payerId().toString());
            statement.setString(3, order.payerName());
            statement.setString(4, order.productId());
            statement.setString(5, order.gateway().name());
            statement.setString(6, order.paymentId());
            statement.setString(7, order.status().name());
            statement.setBigDecimal(8, order.amount());
            statement.setString(9, order.coupon());
            statement.setString(10, order.pixCode());
            statement.setString(11, order.ticketUrl());
            statement.setLong(12, order.createdAt().toEpochMilli());
            statement.setLong(13, order.expiresAt().toEpochMilli());
            statement.setLong(14, order.updatedAt().toEpochMilli());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? order.withId(keys.getLong(1)) : order;
            }
        }
    }

    public void updateCharge(final Order order) throws SQLException {
        final String sql = "UPDATE " + this.table + " SET `payment_id` = ?, `pix_code` = ?, `ticket_url` = ?,"
                + " `updated_at` = ? WHERE `reference_id` = ?";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, order.paymentId());
            statement.setString(2, order.pixCode());
            statement.setString(3, order.ticketUrl());
            statement.setLong(4, order.updatedAt().toEpochMilli());
            statement.setString(5, order.referenceId());
            statement.executeUpdate();
        }
    }

    public boolean transition(final String referenceId, final OrderStatus from, final OrderStatus to,
                              final Instant now) throws SQLException {
        final String sql = "UPDATE " + this.table + " SET `status` = ?, `updated_at` = ?"
                + " WHERE `reference_id` = ? AND `status` = ?";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, to.name());
            statement.setLong(2, now.toEpochMilli());
            statement.setString(3, referenceId);
            statement.setString(4, from.name());
            return statement.executeUpdate() == 1;
        }
    }

    public Optional<Order> findByReference(final String referenceId) throws SQLException {
        final String sql = "SELECT " + COLUMNS + " FROM " + this.table + " WHERE `reference_id` = ?";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, referenceId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    public List<Order> findByStatus(final Collection<OrderStatus> statuses) throws SQLException {
        if (statuses.isEmpty()) {
            return List.of();
        }
        final StringJoiner placeholders = new StringJoiner(", ", "(", ")");
        statuses.forEach(status -> placeholders.add("?"));
        final String sql = "SELECT " + COLUMNS + " FROM " + this.table + " WHERE `status` IN " + placeholders;
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (final OrderStatus status : statuses) {
                statement.setString(index++, status.name());
            }
            return collect(statement);
        }
    }

    public List<Order> findByPayerAndStatus(final UUID payerId, final OrderStatus status) throws SQLException {
        final String sql = "SELECT " + COLUMNS + " FROM " + this.table
                + " WHERE `payer_id` = ? AND `status` = ? ORDER BY `id` ASC";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, payerId.toString());
            statement.setString(2, status.name());
            return collect(statement);
        }
    }

    public List<Order> findRecentByPayer(final UUID payerId, final int limit) throws SQLException {
        final String sql = "SELECT " + COLUMNS + " FROM " + this.table
                + " WHERE `payer_id` = ? ORDER BY `id` DESC LIMIT ?";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, payerId.toString());
            statement.setInt(2, Math.max(1, Math.min(100, limit)));
            return collect(statement);
        }
    }

    private static List<Order> collect(final PreparedStatement statement) throws SQLException {
        final List<Order> orders = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                orders.add(map(result));
            }
        }
        return orders;
    }

    private static Order map(final ResultSet result) throws SQLException {
        final GatewayType gateway = GatewayType.parse(result.getString("gateway"));
        return new Order(
                result.getLong("id"),
                result.getString("reference_id"),
                UUID.fromString(result.getString("payer_id")),
                result.getString("payer_name"),
                result.getString("product_id"),
                gateway == null ? GatewayType.MERCADO_PAGO : gateway,
                result.getString("payment_id"),
                OrderStatus.parse(result.getString("status")),
                result.getBigDecimal("amount"),
                result.getString("coupon"),
                result.getString("pix_code"),
                result.getString("ticket_url"),
                Instant.ofEpochMilli(result.getLong("created_at")),
                Instant.ofEpochMilli(result.getLong("expires_at")),
                Instant.ofEpochMilli(result.getLong("updated_at")));
    }
}
