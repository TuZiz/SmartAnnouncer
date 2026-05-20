package ym.smartannouncer.storage;

import ym.smartannouncer.config.model.AnnouncementDefinition;
import ym.smartannouncer.config.model.DatabaseSettings;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DispatchDedupeService {
    private final Logger logger;
    private final AtomicReference<StoreHolder> storeHolder = new AtomicReference<>(new StoreHolder(DatabaseSettings.disabled(), AnnouncementDispatchStore.DISABLED));

    public DispatchDedupeService(Logger logger) {
        this.logger = logger;
    }

    public void apply(DatabaseSettings settings) {
        StoreHolder previous = storeHolder.get();
        if (sameSettings(previous.settings(), settings)) {
            return;
        }
        AnnouncementDispatchStore nextStore = settings.enabled()
            ? new JdbcAnnouncementDispatchStore(settings, logger)
            : AnnouncementDispatchStore.DISABLED;
        storeHolder.set(new StoreHolder(settings, nextStore));
        close(previous.store());
        if (settings.enabled()) {
            nextStore.cleanup();
        }
    }

    public boolean claim(AnnouncementDefinition announcement, Instant now) {
        return claimCooldown(announcement, "scheduled", now, 0L);
    }

    public boolean claimCooldown(AnnouncementDefinition announcement, String scopeKey, Instant now, long cooldownSeconds) {
        StoreHolder holder = storeHolder.get();
        DatabaseSettings settings = holder.settings();
        if (!settings.enabled()) {
            return true;
        }
        long effectiveCooldownSeconds = cooldownSeconds > 0L ? cooldownSeconds : settings.dispatchDedupeSeconds();
        return holder.store().claimCooldown(announcement.id(), scopeKey, now, Math.max(1L, effectiveCooldownSeconds));
    }

    public boolean claimOnce(AnnouncementDefinition announcement, String scopeKey, Instant now) {
        StoreHolder holder = storeHolder.get();
        DatabaseSettings settings = holder.settings();
        if (!settings.enabled()) {
            return true;
        }
        return holder.store().claimOnce(announcement.id(), scopeKey, now);
    }

    public boolean claimPlayerOnce(AnnouncementDefinition announcement, UUID playerId, Instant now) {
        return claimOnce(announcement, "player:" + playerId, now);
    }

    public void close() {
        close(storeHolder.getAndSet(new StoreHolder(DatabaseSettings.disabled(), AnnouncementDispatchStore.DISABLED)).store());
    }

    private boolean sameSettings(DatabaseSettings left, DatabaseSettings right) {
        return Objects.equals(left, right);
    }

    private void close(AnnouncementDispatchStore store) {
        try {
            store.close();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to close announcement dispatch store.", ex);
        }
    }

    private record StoreHolder(DatabaseSettings settings, AnnouncementDispatchStore store) {
    }
}
