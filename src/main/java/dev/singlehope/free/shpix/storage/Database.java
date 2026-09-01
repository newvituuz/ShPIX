package dev.singlehope.free.shpix.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.singlehope.free.shpix.config.PluginConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Database implements AutoCloseable {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    private final Logger logger;
    private volatile HikariDataSource dataSource;

    public Database(final Logger logger) {
        this.logger = logger;
    }

    public synchronized boolean connect(final PluginConfig config) {
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            return true;
        }
        final HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ShPIX-Pool");
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.databaseUser());
        hikari.setPassword(config.databasePassword());
        hikari.setDriverClassName(DRIVER);
        hikari.setMaximumPoolSize(config.databasePoolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10_000L);
        hikari.setValidationTimeout(5_000L);
        hikari.setIdleTimeout(300_000L);
        hikari.setMaxLifetime(1_500_000L);
        hikari.setKeepaliveTime(60_000L);
        hikari.setInitializationFailTimeout(-1L);
        hikari.setAutoCommit(true);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts", "true");

        try {
            this.dataSource = new HikariDataSource(hikari);
        } catch (Exception exception) {
            this.logger.severe("Não foi possível inicializar o pool de conexões: " + exception.getClass().getSimpleName());
            this.dataSource = null;
            return false;
        }

        try (Connection connection = this.dataSource.getConnection()) {
            if (connection.isValid(5)) {
                return true;
            }
        } catch (SQLException exception) {
            this.logger.log(Level.WARNING, "Banco de dados indisponível no momento (" + exception.getSQLState() + ").");
        }
        closeSource();
        return false;
    }

    private void closeSource() {
        final HikariDataSource source = this.dataSource;
        this.dataSource = null;
        if (source != null && !source.isClosed()) {
            source.close();
        }
    }

    public boolean isAvailable() {
        final HikariDataSource source = this.dataSource;
        return source != null && !source.isClosed();
    }

    public Connection connection() throws SQLException {
        final HikariDataSource source = this.dataSource;
        if (source == null || source.isClosed()) {
            throw new SQLException("Pool de conexões indisponível.");
        }
        return source.getConnection();
    }

    @Override
    public synchronized void close() {
        closeSource();
    }
}
