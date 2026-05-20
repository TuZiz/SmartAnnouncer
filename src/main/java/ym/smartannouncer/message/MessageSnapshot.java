package ym.smartannouncer.message;

import java.util.Map;

public record MessageSnapshot(
    String systemPrefix,
    Map<String, String> templates
) {
    public MessageSnapshot {
        systemPrefix = systemPrefix == null ? "" : systemPrefix;
        templates = Map.copyOf(templates);
    }

    public static MessageSnapshot empty() {
        return new MessageSnapshot("&6[SmartAnnouncer] &r", Map.of());
    }

    public String template(String key) {
        return templates.getOrDefault(key, key);
    }
}
