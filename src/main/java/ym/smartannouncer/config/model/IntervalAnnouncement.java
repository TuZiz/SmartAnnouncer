package ym.smartannouncer.config.model;

import java.util.List;
import java.util.Set;

public record IntervalAnnouncement(
    String id,
    boolean enabled,
    long intervalSeconds,
    MessageOrder order,
    List<AnnouncementMessage> messages,
    Set<String> worlds,
    String permission,
    String prefix
) implements AnnouncementDefinition {
    public IntervalAnnouncement {
        messages = List.copyOf(messages);
        worlds = Set.copyOf(worlds);
        permission = permission == null ? "" : permission;
        prefix = prefix == null ? "" : prefix;
    }

    @Override
    public AnnouncementType type() {
        return AnnouncementType.INTERVAL;
    }
}
