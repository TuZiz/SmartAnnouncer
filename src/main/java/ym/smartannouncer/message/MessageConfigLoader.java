package ym.smartannouncer.message;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.smartannouncer.config.ConfigLoadException;
import ym.smartannouncer.util.ColorUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class MessageConfigLoader {
    private final Path dataFolder;
    private final ClassLoader classLoader;
    private final Logger logger;

    public MessageConfigLoader(Path dataFolder, ClassLoader classLoader, Logger logger) {
        this.dataFolder = dataFolder;
        this.classLoader = classLoader;
        this.logger = logger;
    }

    /*
     * Async-only entry point. This method performs file IO and YAML parsing and
     * must not access Bukkit world/player/entity state.
     */
    public MessageSnapshot load() {
        ensureDefaultMessages();
        Path messagePath = dataFolder.resolve("message.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(messagePath.toFile());
        } catch (IOException | InvalidConfigurationException ex) {
            throw new ConfigLoadException("Failed to load " + messagePath + ": " + ex.getMessage(), ex);
        }

        String systemPrefix = ColorUtil.color(yaml.getString("settings.system-prefix", "&6[SmartAnnouncer] &r"));
        Map<String, String> templates = new LinkedHashMap<>();
        flattenSection(yaml, "", templates);
        templates.remove("settings.system-prefix");
        logger.info("Loaded " + templates.size() + " message templates from message.yml.");
        return new MessageSnapshot(systemPrefix, templates);
    }

    private void ensureDefaultMessages() {
        Path messagePath = dataFolder.resolve("message.yml");
        if (Files.exists(messagePath)) {
            return;
        }
        try {
            Files.createDirectories(dataFolder);
            try (InputStream inputStream = classLoader.getResourceAsStream("message.yml")) {
                if (inputStream == null) {
                    throw new ConfigLoadException("Bundled message.yml resource is missing.");
                }
                Files.copy(inputStream, messagePath);
            }
        } catch (IOException ex) {
            throw new ConfigLoadException("Failed to create default message.yml: " + ex.getMessage(), ex);
        }
    }

    private void flattenSection(ConfigurationSection section, String prefix, Map<String, String> templates) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                flattenSection(section.getConfigurationSection(key), path, templates);
                continue;
            }
            if (section.isString(key)) {
                templates.put(path, ColorUtil.color(section.getString(key, "")));
            }
        }
    }
}
