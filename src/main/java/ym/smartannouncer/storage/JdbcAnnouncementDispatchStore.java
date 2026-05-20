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
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public JdbcAnnouncementDispatchStore(DatabaseSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
        this.dispatchTable = settings.tablePrefix() + "dispatch_log";
    }

    @Override
    public boolean claimDispatch(String announcementId, String bucketKey, Instant dispatchAt) {
        try {
            ensureInitialized();
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement(insertClaimSql())) {
                statement.setString(1, announcementId);
                statement.setString(2, bucketKey);
                statement.setString(3, settings.serverId());
                statement.setLong(4, dispatchAt.getEpochSecond());
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Database dispatch claim failed; sending announcement locally as fallback: " + announcementId, ex);
            return true;
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
                statement.execute(createTableSql());
            }
            initialized.set(true);
            logger.info("Database dispatch de-duplication is ready. table=" + dispatchTable + ", server-id=" + settings.serverId());
        }
    }

    private Connection openConnection() throws SQLException {
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

    private String createTableSql() {
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

    private String insertClaimSql() {
        if (settings.type() == DatabaseType.POSTGRESQL) {
            return "INSERT INTO " + dispatchTable
                + " (announcement_id, bucket_key, server_id, dispatch_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
        }
        return "INSERT IGNORE INTO " + dispatchTable
            + " (announcement_id, bucket_key, server_id, dispatch_at) VALUES (?, ?, ?, ?)";
    }
}
