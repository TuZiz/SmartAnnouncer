package ym.smartannouncer.config.model;

import java.util.List;
import java.util.Set;

public interface AnnouncementDefinition {
    String id();

    AnnouncementType type();

    boolean enabled();

    List<AnnouncementMessage> messages();

    Set<String> worlds();

    String permission();

    String prefix();
}
