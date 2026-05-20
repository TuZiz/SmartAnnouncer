package ym.smartannouncer.service;

import org.bukkit.entity.Player;
import ym.smartannouncer.config.model.FirstJoinAnnouncement;
import ym.smartannouncer.dispatch.MessageDispatcher;
import ym.smartannouncer.storage.DispatchDedupeService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FirstJoinAnnouncementService {
    private final ScheduledExecutorService workerExecutor;
    private final AnnouncementRegistry registry;
    private final MessageDispatcher dispatcher;
    private final DispatchDedupeService dedupeService;
    private final Logger logger;
    private final Map<PendingFirstJoinKey, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();

    public FirstJoinAnnouncementService(
        ScheduledExecutorService workerExecutor,
        AnnouncementRegistry registry,
        MessageDispatcher dispatcher,
        DispatchDedupeService dedupeService,
        Logger logger
    ) {
        this.workerExecutor = workerExecutor;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.dedupeService = dedupeService;
        this.logger = logger;
    }

    /*
     * Event-thread boundary: PlayerJoinEvent provides the first-join flag while
     * the player is joining. This method only schedules async time waiting; the
     * eventual send is handed back to the player's entity scheduler.
     */
    public void handleJoin(Player player, boolean hasPlayedBefore) {
        if (hasPlayedBefore) {
            return;
        }
        UUID playerId = player.getUniqueId();
        for (FirstJoinAnnouncement announcement : registry.snapshot().firstJoinAnnouncements()) {
            if (!announcement.enabled()) {
                continue;
            }
            PendingFirstJoinKey key = new PendingFirstJoinKey(playerId, announcement.id());
            ScheduledFuture<?> future = workerExecutor.schedule(
                () -> trigger(player, announcement.id(), key),
                announcement.delaySeconds(),
                TimeUnit.SECONDS
            );
            ScheduledFuture<?> previous = pendingTasks.put(key, future);
            if (previous != null) {
                previous.cancel(false);
            }
        }
    }

    public void cancelAll() {
        for (ScheduledFuture<?> task : pendingTasks.values()) {
            task.cancel(false);
        }
        pendingTasks.clear();
    }

    private void trigger(Player player, String announcementId, PendingFirstJoinKey key) {
        pendingTasks.remove(key);
        try {
            registry.find(announcementId)
                .filter(FirstJoinAnnouncement.class::isInstance)
                .map(FirstJoinAnnouncement.class::cast)
                .filter(FirstJoinAnnouncement::enabled)
                .filter(announcement -> dedupeService.claimPlayerOnce(announcement, key.playerId(), Instant.now()))
                .ifPresent(announcement -> dispatcher.dispatchToPlayer(announcement, player, announcement.messages()));
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "First-join announcement failed: " + announcementId, throwable);
        }
    }

    private record PendingFirstJoinKey(UUID playerId, String announcementId) {
    }
}
