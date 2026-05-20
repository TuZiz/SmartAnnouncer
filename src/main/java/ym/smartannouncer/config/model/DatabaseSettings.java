package ym.smartannouncer.config.model;

public record DatabaseSettings(
    boolean enabled,
    DatabaseType type,
    String jdbcUrl,
    String host,
    int port,
    String database,
    String username,
    String password,
    String tablePrefix,
    String serverId,
    int dispatchDedupeSeconds,
    int cleanupDays
) {
    public DatabaseSettings {
        jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
        host = host == null ? "" : host.trim();
        database = database == null ? "" : database.trim();
        username = username == null ? "" : username.trim();
        password = password == null ? "" : password;
        tablePrefix = sanitizeTablePrefix(tablePrefix == null ? "smartannouncer_" : tablePrefix.trim());
        serverId = serverId == null || serverId.isBlank() ? "default" : serverId.trim();
        dispatchDedupeSeconds = Math.max(5, dispatchDedupeSeconds);
        cleanupDays = Math.max(1, cleanupDays);
    }

    public static DatabaseSettings disabled() {
        return new DatabaseSettings(false, DatabaseType.MYSQL, "", "localhost", 3306,
            "smartannouncer", "root", "", "smartannouncer_", "default", 60, 14);
    }

    private static String sanitizeTablePrefix(String input) {
        String sanitized = input.replaceAll("[^A-Za-z0-9_]", "");
        return sanitized.isBlank() ? "smartannouncer_" : sanitized;
    }
}
