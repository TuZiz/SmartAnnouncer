package ym.smartannouncer.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    @Test
    void strictYamlBoolAcceptsOnlyTrueFalse() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("settings.debug", "true");
        yaml.set("database.enabled", false);

        assertTrue(ConfigLoader.strictYamlBool(yaml, "settings.debug", false));
        assertFalse(ConfigLoader.strictYamlBool(yaml, "database.enabled", true));
        assertTrue(ConfigLoader.strictYamlBool(yaml, "missing.path", true));
    }

    @Test
    void strictYamlBoolRejectsLooseYamlBooleans() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("database.enabled", "yes");

        ConfigLoadException exception = assertThrows(ConfigLoadException.class,
            () -> ConfigLoader.strictYamlBool(yaml, "database.enabled", false));
        assertTrue(exception.getMessage().contains("database.enabled"));
    }

    @Test
    void strictYamlBoolRejectsLooseYamlFileScalars() throws Exception {
        Path file = Files.createTempFile("smartannouncer-bool", ".yml");
        Files.writeString(file, """
            database:
              enabled: yes
            """);

        ConfigLoadException exception = assertThrows(ConfigLoadException.class,
            () -> ConfigLoader.validateStrictBooleanScalars(file));
        assertTrue(exception.getMessage().contains("database.enabled"));
    }
}
