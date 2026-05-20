package ym.smartannouncer.message;

import java.util.Map;

public final class MessageTemplateResolver {
    private MessageTemplateResolver() {
    }

    public static String resolve(String template, Map<String, String> placeholders) {
        String resolved = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolved;
    }
}
