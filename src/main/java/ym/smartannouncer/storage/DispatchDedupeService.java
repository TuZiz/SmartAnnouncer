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
        StoreHolder holder = storeHolder.get();
        DatabaseSettings settings = holder.settings();
        if (!settings.enabled()) {
            return true;
        }
        long bucket = Math.floorDiv(now.getEpochSecond(), settings.dispatchDedupeSeconds());
        return holder.store().claimDispatch(announcement.id(), Long.toString(bucket), now);
    }

    public boolean claimPlayerOnce(AnnouncementDefinition announcement, UUID playerId, Instant now) {
        StoreHolder holder = storeHolder.get();
        DatabaseSettings settings = holder.settings();
        if (!settings.enabled()) {
            return true;
        }
        return holder.store().claimDispatch(announcement.id(), "player:" + playerId, now);
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
