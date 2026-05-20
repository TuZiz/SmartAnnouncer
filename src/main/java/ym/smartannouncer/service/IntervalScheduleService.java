package ym.smartannouncer.service;

import ym.smartannouncer.config.ConfigSnapshot;
import ym.smartannouncer.config.model.AnnouncementMessage;
import ym.smartannouncer.config.model.IntervalAnnouncement;
import ym.smartannouncer.config.model.MessageOrder;
import ym.smartannouncer.dispatch.MessageDispatcher;
import ym.smartannouncer.storage.DispatchDedupeService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IntervalScheduleService {
    private final ScheduledExecutorService workerExecutor;
    private final AnnouncementRegistry registry;
    private final TimedAnnouncementDispatchQueue dispatchQueue;
    private final MessageDispatcher dispatcher;
    private final DispatchDedupeService dedupeService;
    private final Logger logger;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sequenceIndexes = new ConcurrentHashMap<>();

    public IntervalScheduleService(
        ScheduledExecutorService workerExecutor,
        AnnouncementRegistry registry,
        TimedAnnouncementDispatchQueue dispatchQueue,
        MessageDispatcher dispatcher,
        DispatchDedupeService dedupeService,
        Logger logger
    ) {
        this.workerExecutor = workerExecutor;
        this.registry = registry;
        this.dispatchQueue = dispatchQueue;
        this.dispatcher = dispatcher;
        this.dedupeService = dedupeService;
        this.logger = logger;
    }

    public void reschedule(ConfigSnapshot snapshot) {
        cancelAll();
        for (IntervalAnnouncement announcement : snapshot.intervalAnnouncements()) {
            if (!announcement.enabled()) {
                continue;
            }
            /*
             * Async timing boundary: the ScheduledExecutorService only waits and
             * chooses the next immutable message. MessageDispatcher later switches
             * every recipient to an entity-safe scheduler.
             */
            ScheduledFuture<?> future = workerExecutor.scheduleAtFixedRate(
                () -> trigger(announcement.id()),
                announcement.intervalSeconds(),
                announcement.intervalSeconds(),
                TimeUnit.SECONDS
            );
            tasks.put(announcement.id(), future);
            logger.info("Scheduled interval announcement " + announcement.id()
                + " every " + announcement.intervalSeconds() + " seconds.");
        }
    }

    public void cancelAll() {
        for (ScheduledFuture<?> task : tasks.values()) {
            task.cancel(false);
        }
        tasks.clear();
    }

    private void trigger(String id) {
        try {
            registry.find(id)
                .filter(IntervalAnnouncement.class::isInstance)
                .map(IntervalAnnouncement.class::cast)
                .filter(IntervalAnnouncement::enabled)
                .ifPresent(this::queueDispatch);
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "Interval announcement failed: " + id, throwable);
        }
    }

    /*
     * Async queue boundary: interval timers may hit the same second. We reserve
     * a dispatch slot with a minimum gap so interval announcements do not flood
     * players at once. Actual player delivery still happens later via entity-safe
     * scheduling inside MessageDispatcher.
     */
    private void queueDispatch(IntervalAnnouncement announcement) {
        AnnouncementMessage message = nextMessage(announcement);
        dispatchQueue.enqueue(() -> {
            if (dedupeService.claim(announcement, Instant.now())) {
                dispatcher.dispatchToEligiblePlayers(announcement, List.of(message));
            }
        });
    }

    private AnnouncementMessage nextMessage(IntervalAnnouncement announcement) {
        List<AnnouncementMessage> messages = announcement.messages();
        if (announcement.order() == MessageOrder.RANDOM) {
            return messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        }
        AtomicInteger index = sequenceIndexes.computeIfAbsent(announcement.id(), ignored -> new AtomicInteger());
        int current = Math.floorMod(index.getAndIncrement(), messages.size());
        return messages.get(current);
    }
}
