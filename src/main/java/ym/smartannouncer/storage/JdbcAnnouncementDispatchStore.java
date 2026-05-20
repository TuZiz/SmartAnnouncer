package ym.smartannouncer.storage;

import ym.smartannouncer.config.model.DatabaseSettings;
import ym.smartannouncer.config.model.DatabaseType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JdbcAnnouncementDispatchStore implements AnnouncementDispatchStore {
    private final DatabaseSettings settings;
    private final Logger logger;
    private final String dispatchTable;
    private final String stateTable;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public JdbcAnnouncementDispatchStore(DatabaseSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
        this.dispatchTable = settings.tablePrefix() + "dispatch_log";
        this.stateTable = settings.tablePrefix() + "dispatch_state";
    }

    @Override
    public boolean claimOnce(String announcementId, String scopeKey, Instant dispatchAt) {
        try {
            ensureInitialized();
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement(insertOnceSql())) {
                statement.setString(1, announcementId);
                statement.setString(2, scopeKey);
                statement.setString(3, settings.serverId());
                statement.setLong(4, dispatchAt.getEpochSecond());
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Database dispatch claim failed; skipping announcement to avoid cross-server duplicates: "
                + announcementId + " scope=" + scopeKey, ex);
            return false;
        }
    }

    @Override
    public boolean claimCooldown(String announcementId, String scopeKey, Instant dispatchAt, long cooldownSeconds) {
        try {
            ensureInitialized();
            long dispatchEpochSecond = dispatchAt.getEpochSecond();
            long cutoffEpochSecond = dispatchEpochSecond - Math.max(1L, cooldownSeconds);
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement(claimCooldownSql())) {
                statement.setString(1, announcementId);
                statement.setString(2, scopeKey);
                statement.setString(3, settings.serverId());
                statement.setLong(4, dispatchEpochSecond);
                if (settings.type() == DatabaseType.POSTGRESQL) {
                    statement.setLong(5, cutoffEpochSecond);
                } else {
                    statement.setLong(5, cutoffEpochSecond);
                    statement.setLong(6, cutoffEpochSecond);
                    statement.setLong(7, cutoffEpochSecond);
                }
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Database dispatch cooldown claim failed; skipping announcement to avoid cross-server duplicates: "
                + announcementId + " scope=" + scopeKey, ex);
            return false;
        }
    }

    @Override
    public void cleanup() {
        try {
            ensureInitialized();
            long cutoff = Instant.now().minusSeconds(settings.cleanupDays() * 86400L).getEpochSecond();
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM " + dispatchTable + " WHERE dispatch_at < ?")) {
                statement.setLong(1, cutoff);
                statement.executeUpdate();
            }
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM " + stateTable
                     + " WHERE dispatch_at < ? AND scope_key NOT LIKE 'player:%'")) {
                statement.setLong(1, cutoff);
                statement.executeUpdate();
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Database dispatch cleanup failed.", ex);
        }
    }

    @Override
    public void close() {
    }

    private void ensureInitialized() throws SQLException {
        if (initialized.get()) {
            return;
        }
        synchronized (initialized) {
            if (initialized.get()) {
                return;
            }
            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.execute(createDispatchLogTableSql());
                statement.execute(createStateTableSql());
                int migratedClaims = statement.executeUpdate(migrateLegacyPlayerClaimsSql());
                if (migratedClaims > 0) {
                    logger.info("Migrated " + migratedClaims + " legacy player dispatch claims into " + stateTable + ".");
                }
            }
            initialized.set(true);
            logger.info("Database dispatch de-duplication is ready. log-table=" + dispatchTable
                + ", state-table=" + stateTable + ", server-id=" + settings.serverId());
        }
    }

    private Connection openConnection() throws SQLException {
        loadDriver();
        Properties properties = new Properties();
        properties.setProperty("user", settings.username());
        properties.setProperty("password", settings.password());
        if (settings.type() == DatabaseType.MYSQL) {
            properties.setProperty("useUnicode", "true");
            properties.setProperty("characterEncoding", "utf8");
            properties.setProperty("useSSL", "false");
            properties.setProperty("allowPublicKeyRetrieval", "true");
        }
        return DriverManager.getConnection(jdbcUrl(), properties);
    }

    private void loadDriver() throws SQLException {
        String driverClassName = settings.type() == DatabaseType.POSTGRESQL
            ? "org.postgresql.Driver"
            : "com.mysql.cj.jdbc.Driver";
        try {
            Class.forName(driverClassName, true, JdbcAnnouncementDispatchStore.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new SQLException("JDBC driver class is missing from the plugin jar: " + driverClassName, ex);
        }
    }

    private String jdbcUrl() {
        if (!settings.jdbcUrl().isBlank()) {
            return settings.jdbcUrl();
        }
        if (settings.type() == DatabaseType.POSTGRESQL) {
            return "jdbc:postgresql://" + settings.host() + ':' + settings.port() + '/' + settings.database();
        }
        return "jdbc:mysql://" + settings.host() + ':' + settings.port() + '/' + settings.database()
            + "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private String createDispatchLogTableSql() {
        if (settings.type() == DatabaseType.POSTGRESQL) {
            return "CREATE TABLE IF NOT EXISTS " + dispatchTable + " ("
                + "announcement_id VARCHAR(128) NOT NULL,"
                + "bucket_key VARCHAR(64) NOT NULL,"
                + "server_id VARCHAR(128) NOT NULL,"
                + "dispatch_at BIGINT NOT NULL,"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (announcement_id, bucket_key)"
                + ")";
        }
        return "CREATE TABLE IF NOT EXISTS " + dispatchTable + " ("
            + "announcement_id VARCHAR(128) NOT NULL,"
            + "bucket_key VARCHAR(64) NOT NULL,"
            + "server_id VARCHAR(128) NOT NULL,"
            + "dispatch_at BIGINT NOT NULL,"
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "PRIMARY KEY (announcement_id, bucket_key)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String createStateTableSql() {
        if (settings.type() == DatabaseType.POSTGRESQL) {
            return "CREATE TABLE IF NOT EXISTS " + stateTable + " ("
                + "announcement_id VARCHAR(128) NOT NULL,"
                + "scope_key VARCHAR(128) NOT NULL,"
                + "server_id VARCHAR(128) NOT NULL,"
                + "dispatch_at BIGINT NOT NULL,"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (announcement_id, scope_key)"
                + ")";
        }
        return "CREATE TABLE IF NOT EXISTS " + stateTable + " ("
            + "announcement_id VARCHAR(128) NOT NULL,"
            + "scope_key VARCHAR(128) NOT NULL,"
            + "server_id VARCHAR(128) NOT NULL,"
            + "dispatch_at BIGINT NOT NULL,"
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + "PRIMARY KEY (announcement_id, scope_key)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    private String insertOnceSql() {
        if (settings.type() == DatabaseType.POSTGRESQL) {
            return "INSERT INTO " + stateTable
                + " (announcement_id, scope_key, server_id, dispatch_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
        }
        return "INSERT IGNORE INTO " + stateTable
            + " (announcement_id, scope_key, server_id, dispatch_at) VALUES (?, ?, ?, ?)";
    }

    private String claimCooldownSql() {
        if (settings.type() == DatabaseType.POSTGRESQL) {
            return "INSERT INTO " + stateTable
                + " (announcement_id, scope_key, server_id, dispatch_at) VALUES (?, ?, ?, ?)"
                + " ON CONFLICT (announcement_id, scope_key) DO UPDATE SET"
                + " server_id = EXCLUDED.server_id,"
                + " dispatch_at = EXCLUDED.dispatch_at,"
                + " updated_at = CURRENT_TIMESTAMP"
                + " WHERE " + stateTable + ".dispatch_at <= ?";
        }
        return "INSERT INTO " + stateTable
            + " (announcement_id, scope_key, server_id, dispatch_at) VALUES (?, ?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE"
            + " updated_at = IF(dispatch_at <= ?, CURRENT_TIMESTAMP, updated_at),"
            + " server_id = IF(dispatch_at <= ?, VALUES(server_id), server_id),"
            + " dispatch_at = IF(dispatch_at <= ?, VALUES(dispatch_at), dispatch_at)";
    }

    private String migrateLegacyPlayerClaimsSql() {
        if (settings.type() == DatabaseType.POSTGRESQL) {
            return "INSERT INTO " + stateTable
                + " (announcement_id, scope_key, server_id, dispatch_at)"
                + " SELECT announcement_id, bucket_key, MIN(server_id), MAX(dispatch_at)"
                + " FROM " + dispatchTable
                + " WHERE bucket_key LIKE 'player:%'"
                + " GROUP BY announcement_id, bucket_key"
                + " ON CONFLICT DO NOTHING";
        }
        return "INSERT IGNORE INTO " + stateTable
            + " (announcement_id, scope_key, server_id, dispatch_at)"
            + " SELECT announcement_id, bucket_key, MIN(server_id), MAX(dispatch_at)"
            + " FROM " + dispatchTable
            + " WHERE bucket_key LIKE 'player:%'"
            + " GROUP BY announcement_id, bucket_key";
    }
}
