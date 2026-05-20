package ym.smartannouncer.config.model;

import java.util.List;
import java.util.Set;

public record FirstJoinAnnouncement(
    String id,
    boolean enabled,
    long delaySeconds,
    List<AnnouncementMessage> messages,
    Set<String> worlds,
    String permission,
    String prefix
) implements AnnouncementDefinition {
    public FirstJoinAnnouncement {
        messages = List.copyOf(messages);
        worlds = Set.copyOf(worlds);
        permission = permission == null ? "" : permission;
        prefix = prefix == null ? "" : prefix;
    }

    @Override
    public AnnouncementType type() {
        return AnnouncementType.FIRST_JOIN;
    }
}
