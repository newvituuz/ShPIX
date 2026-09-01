package dev.singlehope.free.shpix.storage;

import dev.singlehope.free.shpix.user.User;
import dev.singlehope.free.shpix.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class UserRepository {

    private final Database database;
    private final String table;

    public UserRepository(final Database database, final String prefix) {
        this.database = database;
        this.table = "`" + prefix + "users`";
    }

    public User loadOrCreate(final UUID uniqueId, final String name) throws SQLException {
        final String sql = "INSERT INTO " + this.table
                + " (`unique_id`, `name`, `total_orders`, `total_paid`, `total_refunded`, `balance`)"
                + " VALUES (?, ?, 0, 0.00, 0.00, 0.00) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`)";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uniqueId.toString());
            statement.setString(2, truncateName(name));
            statement.executeUpdate();
        }
        return find(uniqueId).orElseGet(() -> User.empty(uniqueId, truncateName(name)));
    }

    public Optional<User> find(final UUID uniqueId) throws SQLException {
        final String sql = "SELECT `unique_id`, `name`, `total_orders`, `total_paid`, `total_refunded`, `balance` FROM "
                + this.table + " WHERE `unique_id` = ?";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uniqueId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    public void incrementOrders(final UUID uniqueId) throws SQLException {
        update("UPDATE " + this.table + " SET `total_orders` = `total_orders` + 1 WHERE `unique_id` = ?", uniqueId);
    }

    public void addPaid(final UUID uniqueId, final BigDecimal amount) throws SQLException {
        addAmount("total_paid", uniqueId, amount);
    }

    public void addRefunded(final UUID uniqueId, final BigDecimal amount) throws SQLException {
        addAmount("total_refunded", uniqueId, amount);
    }

    private void addAmount(final String column, final UUID uniqueId, final BigDecimal amount) throws SQLException {
        final BigDecimal value = Money.normalize(amount);
        if (!Money.isPositive(value)) {
            return;
        }
        final String sql = "UPDATE " + this.table + " SET `" + column + "` = `" + column + "` + ? WHERE `unique_id` = ?";
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, value);
            statement.setString(2, uniqueId.toString());
            statement.executeUpdate();
        }
    }

    private void update(final String sql, final UUID uniqueId) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uniqueId.toString());
            statement.executeUpdate();
        }
    }

    private static User map(final ResultSet result) throws SQLException {
        return new User(
                UUID.fromString(result.getString("unique_id")),
                result.getString("name"),
                result.getInt("total_orders"),
                result.getBigDecimal("total_paid"),
                result.getBigDecimal("total_refunded"),
                result.getBigDecimal("balance"));
    }

    private static String truncateName(final String name) {
        if (name == null) {
            return "";
        }
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}
