package ym.smartannouncer.service;

import ym.smartannouncer.config.ConfigSnapshot;
import ym.smartannouncer.config.model.AnnouncementDefinition;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class AnnouncementRegistry {
    private final AtomicReference<ConfigSnapshot> snapshot = new AtomicReference<>(ConfigSnapshot.empty());

    public ConfigSnapshot snapshot() {
        return snapshot.get();
    }

    public void replace(ConfigSnapshot newSnapshot) {
        snapshot.set(newSnapshot);
    }

    public Optional<AnnouncementDefinition> find(String id) {
        return snapshot.get().find(id);
    }
}
