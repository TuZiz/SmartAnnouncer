package ym.smartannouncer.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.smartannouncer.config.model.AnnouncementDefinition;
import ym.smartannouncer.config.model.AnnouncementMessage;
import ym.smartannouncer.config.model.ClockAnnouncement;
import ym.smartannouncer.config.model.CuboidRegionShape;
import ym.smartannouncer.config.model.DatabaseSettings;
import ym.smartannouncer.config.model.DatabaseType;
import ym.smartannouncer.config.model.FirstJoinAnnouncement;
import ym.smartannouncer.config.model.IntervalAnnouncement;
import ym.smartannouncer.config.model.LocationAnnouncement;
import ym.smartannouncer.config.model.LocationTarget;
import ym.smartannouncer.config.model.LocationTrigger;
import ym.smartannouncer.config.model.MessageClickAction;
import ym.smartannouncer.config.model.MessageOrder;
import ym.smartannouncer.config.model.RegionKind;
import ym.smartannouncer.config.model.RegionShape;
import ym.smartannouncer.config.model.SphereRegionShape;
import ym.smartannouncer.util.ColorUtil;
import ym.smartannouncer.util.TimeUtil;
import ym.smartannouncer.util.ValidationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class ConfigLoader {
    private final Path dataFolder;
    private final ClassLoader classLoader;
    private final Logger logger;

    public ConfigLoader(Path dataFolder, ClassLoader classLoader, Logger logger) {
        this.dataFolder = dataFolder;
        this.classLoader = classLoader;
        this.logger = logger;
    }

    /*
     * Async-only entry point. This method performs file IO and YAML parsing and
     * must never be called from a Bukkit main/global/entity/region thread. It
     * uses only Bukkit's standalone configuration classes and does not touch
     * World, Player, Entity or Chunk state.
     */
    public ConfigSnapshot load() {
        ensureDefaultFiles();
        Path configPath = dataFolder.resolve("config.yml");
        Path announcementsPath = dataFolder.resolve("announcements.yml");
        YamlConfiguration yaml = loadYaml(configPath);
        YamlConfiguration announcementsYaml = loadYaml(announcementsPath);

        String timezone = yaml.getString("settings.timezone", ZoneId.systemDefault().getId());
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new ConfigLoadException("settings.timezone is invalid: " + timezone, ex);
        }

        String globalPrefix = ColorUtil.color(yaml.getString("settings.prefix", ""));
        boolean debug = yaml.getBoolean("settings.debug", false);
        DatabaseSettings databaseSettings = parseDatabaseSettings(yaml);
        List<Map<?, ?>> rawAnnouncements = announcementsYaml.getMapList("announcements");

        Map<String, AnnouncementDefinition> byId = new LinkedHashMap<>();
        List<IntervalAnnouncement> intervals = new ArrayList<>();
        List<ClockAnnouncement> clocks = new ArrayList<>();
        List<LocationAnnouncement> locations = new ArrayList<>();
        List<FirstJoinAnnouncement> firstJoins = new ArrayList<>();

        for (int index = 0; index < rawAnnouncements.size(); index++) {
            Map<?, ?> raw = rawAnnouncements.get(index);
            String path = "announcements[" + index + "]";
            String id = requiredString(raw, "id", path);
            ValidationUtil.require(!byId.containsKey(id), path + ".id duplicates existing announcement id: " + id);
            String type = requiredString(raw, "type", path).toUpperCase(Locale.ROOT);
            AnnouncementDefinition definition = switch (type) {
                case "INTERVAL" -> parseInterval(raw, path, id, globalPrefix);
                case "CLOCK" -> parseClock(raw, path, id, globalPrefix);
                case "LOCATION" -> parseLocation(raw, path, id, globalPrefix);
                case "FIRST_JOIN" -> parseFirstJoin(raw, path, id, globalPrefix);
                default -> throw new ConfigLoadException(path + ".type must be interval, clock, location or first_join.");
            };
            byId.put(id, definition);
            if (definition instanceof IntervalAnnouncement interval) {
                intervals.add(interval);
            } else if (definition instanceof ClockAnnouncement clock) {
                clocks.add(clock);
            } else if (definition instanceof LocationAnnouncement location) {
                locations.add(location);
            } else if (definition instanceof FirstJoinAnnouncement firstJoin) {
                firstJoins.add(firstJoin);
            }
        }

        logger.info("Loaded " + byId.size() + " announcements: " + intervals.size()
            + " interval, " + clocks.size() + " clock, " + locations.size()
            + " location, " + firstJoins.size() + " first_join.");

        return new ConfigSnapshot(zoneId, globalPrefix, debug, databaseSettings, byId, intervals, clocks, locations, firstJoins);
    }

    private DatabaseSettings parseDatabaseSettings(YamlConfiguration yaml) {
        boolean enabled = yaml.getBoolean("database.enabled", false);
        DatabaseType type = databaseType(yaml.getString("database.type", "mysql"));
        int defaultPort = type == DatabaseType.POSTGRESQL ? 5432 : 3306;
        return new DatabaseSettings(
            enabled,
            type,
            yaml.getString("database.jdbc-url", ""),
            yaml.getString("database.host", "localhost"),
            yaml.getInt("database.port", defaultPort),
            yaml.getString("database.database", "smartannouncer"),
            yaml.getString("database.username", "root"),
            yaml.getString("database.password", ""),
            yaml.getString("database.table-prefix", "smartannouncer_"),
            yaml.getString("database.server-id", "default"),
            yaml.getInt("database.dispatch-dedupe-seconds", 60),
            yaml.getInt("database.cleanup-days", 14)
        );
    }

    private DatabaseType databaseType(String rawType) {
        String normalized = rawType == null ? "mysql" : rawType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("POSTGRES") || normalized.equals("POSTGRESQL")) {
            return DatabaseType.POSTGRESQL;
        }
        if (normalized.equals("MYSQL")) {
            return DatabaseType.MYSQL;
        }
        throw new ConfigLoadException("database.type must be mysql or postgresql.");
    }

    private void ensureDefaultFiles() {
        Path configPath = dataFolder.resolve("config.yml");
        Path announcementsPath = dataFolder.resolve("announcements.yml");
        try {
            Files.createDirectories(dataFolder);
            if (!Files.exists(configPath)) {
                copyBundledResource("config.yml", configPath);
            }
            if (!Files.exists(announcementsPath) && !migrateLegacyAnnouncements(configPath, announcementsPath)) {
                copyBundledResource("announcements.yml", announcementsPath);
            }
        } catch (IOException ex) {
            throw new ConfigLoadException("Failed to create default configuration files: " + ex.getMessage(), ex);
        }
    }

    private boolean migrateLegacyAnnouncements(Path configPath, Path announcementsPath) throws IOException {
        YamlConfiguration legacyConfig = loadYaml(configPath);
        List<?> legacyAnnouncements = legacyConfig.getList("announcements");
        if (legacyAnnouncements == null || legacyAnnouncements.isEmpty()) {
            return false;
        }
        YamlConfiguration migrated = new YamlConfiguration();
        migrated.set("announcements", legacyAnnouncements);
        migrated.save(announcementsPath.toFile());
        logger.warning("Detected legacy announcements in config.yml; migrated them to announcements.yml. The old section was left unchanged.");
        return true;
    }

    private void copyBundledResource(String resourceName, Path targetPath) throws IOException {
        try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new ConfigLoadException("Bundled " + resourceName + " resource is missing.");
            }
            Files.copy(inputStream, targetPath);
        }
    }

    private YamlConfiguration loadYaml(Path path) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            return yaml;
        } catch (IOException | InvalidConfigurationException ex) {
            throw new ConfigLoadException("Failed to load " + path + ": " + ex.getMessage(), ex);
        }
    }

    private IntervalAnnouncement parseInterval(Map<?, ?> raw, String path, String id, String globalPrefix) {
        boolean enabled = bool(raw, "enabled", true);
        long intervalSeconds = intervalSeconds(raw, path);
        MessageOrder order = enumValue(raw, "order", MessageOrder.SEQUENTIAL, MessageOrder.class, path);
        List<AnnouncementMessage> messages = parseMessages(raw.get("messages"), path + ".messages");
        Set<String> worlds = stringSet(raw.get("worlds"));
        String permission = optionalString(raw, "permission", "");
        String prefix = ColorUtil.color(optionalString(raw, "prefix", globalPrefix));
        return new IntervalAnnouncement(id, enabled, intervalSeconds, order, messages, worlds, permission, prefix);
    }

    private long intervalSeconds(Map<?, ?> raw, String path) {
        boolean hasSeconds = raw.containsKey("interval-seconds");
        boolean hasMinutes = raw.containsKey("interval-minutes");
        ValidationUtil.require(hasSeconds || hasMinutes, path + " must define interval-seconds or interval-minutes.");
        if (hasSeconds) {
            return positiveLong(raw, "interval-seconds", path);
        }
        long minutes = positiveLong(raw, "interval-minutes", path);
        ValidationUtil.require(minutes <= Long.MAX_VALUE / 60L, path + ".interval-minutes is too large.");
        return minutes * 60L;
    }

    private ClockAnnouncement parseClock(Map<?, ?> raw, String path, String id, String globalPrefix) {
        boolean enabled = bool(raw, "enabled", true);
        List<String> rawTimes = stringList(raw.get("times"));
        ValidationUtil.require(!rawTimes.isEmpty(), path + ".times must contain at least one HH:mm value.");
        List<LocalTime> times = new ArrayList<>();
        for (String rawTime : rawTimes) {
            times.add(TimeUtil.parseClockTime(rawTime));
        }
        List<AnnouncementMessage> messages = parseMessages(raw.get("messages"), path + ".messages");
        Set<String> worlds = stringSet(raw.get("worlds"));
        String permission = optionalString(raw, "permission", "");
        String prefix = ColorUtil.color(optionalString(raw, "prefix", globalPrefix));
        return new ClockAnnouncement(id, enabled, times, messages, worlds, permission, prefix);
    }

    private LocationAnnouncement parseLocation(Map<?, ?> raw, String path, String id, String globalPrefix) {
        boolean enabled = bool(raw, "enabled", true);
        LocationTrigger trigger = enumValue(raw, "trigger", LocationTrigger.ENTER, LocationTrigger.class, path);
        LocationTarget target = enumValue(raw, "target", LocationTarget.PLAYER, LocationTarget.class, path);
        long cooldownSeconds = nonNegativeLong(raw, "cooldown-seconds", 0L, path);
        double nearbyRadius = positiveDouble(raw, "nearby-radius", 32.0D, path);
        RegionShape shape = parseShape(requiredMap(raw.get("shape"), path + ".shape"), path + ".shape");
        List<AnnouncementMessage> messages = parseMessages(raw.get("messages"), path + ".messages");
        Set<String> worlds = stringSet(raw.get("worlds"));
        String permission = optionalString(raw, "permission", "");
        String prefix = ColorUtil.color(optionalString(raw, "prefix", globalPrefix));
        return new LocationAnnouncement(id, enabled, trigger, target, cooldownSeconds, nearbyRadius, shape, messages, worlds, permission, prefix);
    }

    private FirstJoinAnnouncement parseFirstJoin(Map<?, ?> raw, String path, String id, String globalPrefix) {
        boolean enabled = bool(raw, "enabled", true);
        long delaySeconds = nonNegativeLong(raw, "delay-seconds", 5L, path);
        List<AnnouncementMessage> messages = parseMessages(raw.get("messages"), path + ".messages");
        Set<String> worlds = stringSet(raw.get("worlds"));
        String permission = optionalString(raw, "permission", "");
        String prefix = ColorUtil.color(optionalString(raw, "prefix", globalPrefix));
        return new FirstJoinAnnouncement(id, enabled, delaySeconds, messages, worlds, permission, prefix);
    }

    private RegionShape parseShape(Map<?, ?> raw, String path) {
        RegionKind kind = enumValue(raw, "kind", null, RegionKind.class, path);
        String world = requiredString(raw, "world", path);
        return switch (kind) {
            case SPHERE -> new SphereRegionShape(
                world,
                number(raw, "x", path),
                number(raw, "y", path),
                number(raw, "z", path),
                positiveDouble(raw, "radius", -1.0D, path)
            );
            case CUBOID -> new CuboidRegionShape(
                world,
                number(raw, "min-x", path),
                number(raw, "min-y", path),
                number(raw, "min-z", path),
                number(raw, "max-x", path),
                number(raw, "max-y", path),
                number(raw, "max-z", path)
            );
        };
    }

    private List<AnnouncementMessage> parseMessages(Object value, String path) {
        if (!(value instanceof List<?> list)) {
            throw new ConfigLoadException(path + " must contain at least one message.");
        }
        List<AnnouncementMessage> messages = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            Object item = list.get(index);
            String itemPath = path + "[" + index + "]";
            if (item instanceof String string) {
                if (!string.isBlank()) {
                    messages.add(new AnnouncementMessage(ColorUtil.color(string), null, "", List.of()));
                }
                continue;
            }
            if (item instanceof Map<?, ?> map) {
                messages.add(parseMessageMap(map, itemPath));
                continue;
            }
            if (item instanceof ConfigurationSection section) {
                messages.add(parseMessageMap(section.getValues(false), itemPath));
                continue;
            }
            throw new ConfigLoadException(itemPath + " must be either a string or a message map.");
        }
        ValidationUtil.require(!messages.isEmpty(), path + " must contain at least one non-empty message.");
        return List.copyOf(messages);
    }

    private AnnouncementMessage parseMessageMap(Map<?, ?> raw, String path) {
        String text = ColorUtil.color(optionalString(raw, "text", ""));
        ValidationUtil.require(!text.isBlank(), path + ".text must be a non-empty string.");
        MessageClickAction clickAction = null;
        String clickValue = "";
        Object clickRaw = raw.get("click");
        if (clickRaw instanceof Map<?, ?> clickMap) {
            clickAction = enumValue(clickMap, "action", null, MessageClickAction.class, path + ".click");
            clickValue = requiredString(clickMap, "value", path + ".click");
        } else if (clickRaw instanceof ConfigurationSection section) {
            Map<String, Object> clickMap = section.getValues(false);
            clickAction = enumValue(clickMap, "action", null, MessageClickAction.class, path + ".click");
            clickValue = requiredString(clickMap, "value", path + ".click");
        } else if (clickRaw != null) {
            throw new ConfigLoadException(path + ".click must be a map with action and value.");
        }
        Object hoverRaw = raw.get("hover");
        List<String> hover;
        if (hoverRaw instanceof List<?>) {
            hover = stringList(hoverRaw).stream().map(ColorUtil::color).toList();
        } else if (hoverRaw instanceof String hoverText && !hoverText.isBlank()) {
            hover = List.of(ColorUtil.color(hoverText));
        } else {
            hover = List.of();
        }
        return new AnnouncementMessage(text, clickAction, clickValue, hover);
    }

    private String requiredString(Map<?, ?> raw, String key, String path) {
        Object value = raw.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new ConfigLoadException(path + "." + key + " must be a non-empty string.");
        }
        return string.trim();
    }

    private String optionalString(Map<?, ?> raw, String key, String defaultValue) {
        Object value = raw.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value).trim();
    }

    private boolean bool(Map<?, ?> raw, String key, boolean defaultValue) {
        Object value = raw.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private long positiveLong(Map<?, ?> raw, String key, String path) {
        long value = nonNegativeLong(raw, key, -1L, path);
        ValidationUtil.require(value > 0L, path + "." + key + " must be greater than 0.");
        return value;
    }

    private long nonNegativeLong(Map<?, ?> raw, String key, long defaultValue, String path) {
        Object value = raw.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            ValidationUtil.require(parsed >= 0L, path + "." + key + " must be 0 or greater.");
            return parsed;
        } catch (NumberFormatException ex) {
            throw new ConfigLoadException(path + "." + key + " must be a whole number.", ex);
        }
    }

    private double positiveDouble(Map<?, ?> raw, String key, double defaultValue, String path) {
        Object value = raw.get(key);
        if (value == null) {
            ValidationUtil.require(defaultValue > 0.0D, path + "." + key + " is required.");
            return defaultValue;
        }
        double parsed = number(raw, key, path);
        ValidationUtil.require(parsed > 0.0D, path + "." + key + " must be greater than 0.");
        return parsed;
    }

    private double number(Map<?, ?> raw, String key, String path) {
        Object value = raw.get(key);
        if (value == null) {
            throw new ConfigLoadException(path + "." + key + " is required.");
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ConfigLoadException(path + "." + key + " must be a number.", ex);
        }
    }

    private <E extends Enum<E>> E enumValue(Map<?, ?> raw, String key, E defaultValue, Class<E> enumClass, String path) {
        Object value = raw.get(key);
        if (value == null) {
            if (defaultValue != null) {
                return defaultValue;
            }
            throw new ConfigLoadException(path + "." + key + " is required.");
        }
        try {
            return Enum.valueOf(enumClass, String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ConfigLoadException(path + "." + key + " has invalid value: " + value, ex);
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                String string = String.valueOf(item).trim();
                if (!string.isEmpty()) {
                    result.add(string);
                }
            }
        }
        return result;
    }

    private Set<String> stringSet(Object value) {
        return new LinkedHashSet<>(stringList(value));
    }

    private Map<?, ?> requiredMap(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new ConfigLoadException(path + " must be a map.");
    }
}
