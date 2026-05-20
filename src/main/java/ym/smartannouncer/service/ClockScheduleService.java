package ym.smartannouncer.service;

import ym.smartannouncer.config.ConfigSnapshot;
import ym.smartannouncer.config.model.ClockAnnouncement;
import ym.smartannouncer.dispatch.MessageDispatcher;
import ym.smartannouncer.storage.DispatchDedupeService;
import ym.smartannouncer.util.TimeUtil;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClockScheduleService {
    private static final DateTimeFormatter CLOCK_SCOPE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final ScheduledExecutorService workerExecutor;
    private final AnnouncementRegistry registry;
    private final TimedAnnouncementDispatchQueue dispatchQueue;
    private final MessageDispatcher dispatcher;
    private final DispatchDedupeService dedupeService;
    private final Logger logger;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    public ClockScheduleService(
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
        for (ClockAnnouncement announcement : snapshot.clockAnnouncements()) {
            if (announcement.enabled()) {
                scheduleNext(announcement.id(), snapshot.zoneId());
            }
        }
    }

    public void cancelAll() {
        generation.incrementAndGet();
        for (ScheduledFuture<?> task : tasks.values()) {
            task.cancel(false);
        }
        tasks.clear();
    }

    private void scheduleNext(String id, ZoneId zoneId) {
        long currentGeneration = generation.get();
        registry.find(id)
            .filter(ClockAnnouncement.class::isInstance)
            .map(ClockAnnouncement.class::cast)
            .filter(ClockAnnouncement::enabled)
            .ifPresent(announcement -> {
                /*
                 * Async timing boundary: java.time computes true wall-clock delay
                 * off the Bukkit thread. The send path later re-enters entity
                 * schedulers per recipient.
                 */
                ZonedDateTime nextRun = TimeUtil.nextOccurrence(announcement.times(), zoneId);
                Duration delay = Duration.between(ZonedDateTime.now(zoneId), nextRun);
                String dispatchScope = "clock:" + CLOCK_SCOPE_FORMATTER.format(nextRun) + ':' + zoneId.getId();
                ScheduledFuture<?> future = workerExecutor.schedule(
                    () -> triggerAndReschedule(id, currentGeneration, dispatchScope),
                    Math.max(0L, delay.toMillis()),
                    TimeUnit.MILLISECONDS
                );
                tasks.put(id, future);
                logger.info("Scheduled clock announcement " + id + " in " + delay.toSeconds() + " seconds.");
            });
    }

    private void triggerAndReschedule(String id, long currentGeneration, String dispatchScope) {
        try {
            if (currentGeneration != generation.get()) {
                return;
            }
            ConfigSnapshot snapshot = registry.snapshot();
            registry.find(id)
                .filter(ClockAnnouncement.class::isInstance)
                .map(ClockAnnouncement.class::cast)
                .filter(ClockAnnouncement::enabled)
                .ifPresent(announcement -> {
                    dispatchQueue.enqueue(() -> {
                        if (dedupeService.claimOnce(announcement, dispatchScope, Instant.now())) {
                            dispatcher.dispatchToEligiblePlayers(announcement, announcement.messages());
                        }
                    });
                    scheduleNext(id, snapshot.zoneId());
                });
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "Clock announcement failed: " + id, throwable);
        }
    }
}
