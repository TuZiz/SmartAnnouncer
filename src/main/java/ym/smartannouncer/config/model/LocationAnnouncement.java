package ym.smartannouncer.config.model;

import java.util.List;
import java.util.Set;

public record LocationAnnouncement(
    String id,
    boolean enabled,
    LocationTrigger trigger,
    LocationTarget target,
    long cooldownSeconds,
    double nearbyRadius,
    RegionShape shape,
    List<AnnouncementMessage> messages,
    Set<String> worlds,
    String permission,
    String prefix
) implements AnnouncementDefinition {
    public LocationAnnouncement {
        messages = List.copyOf(messages);
        worlds = worlds == null || worlds.isEmpty() ? Set.of(shape.worldName()) : Set.copyOf(worlds);
        permission = permission == null ? "" : permission;
        prefix = prefix == null ? "" : prefix;
    }

    @Override
    public AnnouncementType type() {
        return AnnouncementType.LOCATION;
    }
}
