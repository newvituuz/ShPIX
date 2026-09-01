package dev.singlehope.free.shpix.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public final class SchemaManager {

    private final Database database;
    private final String prefix;
    private final Logger logger;

    public SchemaManager(final Database database, final String prefix, final Logger logger) {
        this.database = database;
        this.prefix = prefix;
        this.logger = logger;
    }

    public void apply() throws SQLException {
        try (Connection connection = this.database.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `%susers` (
                      `unique_id` VARCHAR(36) NOT NULL,
                      `name` VARCHAR(16) NOT NULL,
                      `total_orders` INT NOT NULL DEFAULT 0,
                      `total_paid` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                      `total_refunded` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                      `balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                      PRIMARY KEY (`unique_id`),
                      KEY `idx_%susers_name` (`name`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(this.prefix, this.prefix));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS `%sorders` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT,
                      `reference_id` VARCHAR(64) NOT NULL,
                      `payer_id` VARCHAR(36) NOT NULL,
                      `payer_name` VARCHAR(16) NOT NULL,
                      `product_id` VARCHAR(64) NOT NULL,
                      `gateway` VARCHAR(32) NOT NULL,
                      `payment_id` VARCHAR(64) DEFAULT NULL,
                      `status` VARCHAR(16) NOT NULL,
                      `amount` DECIMAL(12,2) NOT NULL,
                      `coupon` VARCHAR(64) DEFAULT NULL,
                      `pix_code` TEXT DEFAULT NULL,
                      `ticket_url` VARCHAR(512) DEFAULT NULL,
                      `created_at` BIGINT NOT NULL,
                      `expires_at` BIGINT NOT NULL,
                      `updated_at` BIGINT NOT NULL,
                      PRIMARY KEY (`id`),
                      UNIQUE KEY `uk_%sorders_reference` (`reference_id`),
                      UNIQUE KEY `uk_%sorders_payment` (`payment_id`),
                      KEY `idx_%sorders_status` (`status`),
                      KEY `idx_%sorders_payer` (`payer_id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(this.prefix, this.prefix, this.prefix, this.prefix, this.prefix));
        }
        importLegacyUsers();
    }

    private void importLegacyUsers() {
        try (Connection connection = this.database.connection()) {
            if (!tableExists(connection, "users") || !columnExists(connection, "users", "uniqueId")) {
                return;
            }
            if (countRows(connection, this.prefix + "users") > 0) {
                return;
            }
            final String sql = "INSERT IGNORE INTO `" + this.prefix + "users` "
                    + "(`unique_id`, `name`, `total_orders`, `total_paid`, `total_refunded`, `balance`) "
                    + "SELECT `uniqueId`, COALESCE(`name`, ''), COALESCE(`totalOrders`, 0), "
                    + "COALESCE(`totalPaid`, 0), COALESCE(`totalRefunded`, 0), COALESCE(`balance`, 0) FROM `users`";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                final int imported = statement.executeUpdate();
                if (imported > 0) {
                    this.logger.info("Migrados " + imported + " registros de usuários da instalação anterior.");
                }
            }
        } catch (SQLException exception) {
            this.logger.warning("Não foi possível migrar os dados da instalação anterior: " + exception.getSQLState());
        }
    }

    private static boolean tableExists(final Connection connection, final String table) throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet result = metaData.getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private static boolean columnExists(final Connection connection, final String table, final String column) throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet result = metaData.getColumns(connection.getCatalog(), null, table, column)) {
            return result.next();
        }
    }

    private static long countRows(final Connection connection, final String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM `" + table + "`");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }
}
