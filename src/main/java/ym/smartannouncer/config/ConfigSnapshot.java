package ym.smartannouncer.config;

import ym.smartannouncer.config.model.AnnouncementDefinition;
import ym.smartannouncer.config.model.ClockAnnouncement;
import ym.smartannouncer.config.model.DatabaseSettings;
import ym.smartannouncer.config.model.FirstJoinAnnouncement;
import ym.smartannouncer.config.model.IntervalAnnouncement;
import ym.smartannouncer.config.model.LocationAnnouncement;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ConfigSnapshot(
    ZoneId zoneId,
    String prefix,
    boolean debug,
    DatabaseSettings databaseSettings,
    Map<String, AnnouncementDefinition> announcementsById,
    List<IntervalAnnouncement> intervalAnnouncements,
    List<ClockAnnouncement> clockAnnouncements,
    List<LocationAnnouncement> locationAnnouncements,
    List<FirstJoinAnnouncement> firstJoinAnnouncements
) {
    public ConfigSnapshot {
        announcementsById = Map.copyOf(announcementsById);
        intervalAnnouncements = List.copyOf(intervalAnnouncements);
        clockAnnouncements = List.copyOf(clockAnnouncements);
        locationAnnouncements = List.copyOf(locationAnnouncements);
        firstJoinAnnouncements = List.copyOf(firstJoinAnnouncements);
    }

    public static ConfigSnapshot empty() {
        return new ConfigSnapshot(ZoneId.systemDefault(), "", false, DatabaseSettings.disabled(), Map.of(), List.of(), List.of(), List.of(), List.of());
    }

    public Optional<AnnouncementDefinition> find(String id) {
        return Optional.ofNullable(announcementsById.get(id));
    }
}
