package ym.smartannouncer.service;

import org.bukkit.command.CommandSender;
import ym.smartannouncer.config.ConfigLoadException;
import ym.smartannouncer.config.ConfigLoader;
import ym.smartannouncer.config.ConfigSnapshot;
import ym.smartannouncer.dispatch.MessageDispatcher;
import ym.smartannouncer.message.MessageConfigLoader;
import ym.smartannouncer.message.MessageRegistry;
import ym.smartannouncer.message.MessageSnapshot;
import ym.smartannouncer.storage.DispatchDedupeService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AnnouncementService {
    private final ScheduledExecutorService workerExecutor;
    private final AnnouncementRegistry registry;
    private final ConfigLoader configLoader;
    private final MessageRegistry messageRegistry;
    private final MessageConfigLoader messageConfigLoader;
    private final IntervalScheduleService intervalScheduleService;
    private final ClockScheduleService clockScheduleService;
    private final TimedAnnouncementDispatchQueue timedDispatchQueue;
    private final LocationAnnouncementService locationAnnouncementService;
    private final FirstJoinAnnouncementService firstJoinAnnouncementService;
    private final MessageDispatcher dispatcher;
    private final DispatchDedupeService dedupeService;
    private final Logger logger;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicLong reloadGeneration = new AtomicLong();

    public AnnouncementService(
        ScheduledExecutorService workerExecutor,
        AnnouncementRegistry registry,
        ConfigLoader configLoader,
        MessageRegistry messageRegistry,
        MessageConfigLoader messageConfigLoader,
        IntervalScheduleService intervalScheduleService,
        ClockScheduleService clockScheduleService,
        TimedAnnouncementDispatchQueue timedDispatchQueue,
        LocationAnnouncementService locationAnnouncementService,
        FirstJoinAnnouncementService firstJoinAnnouncementService,
        MessageDispatcher dispatcher,
        DispatchDedupeService dedupeService,
        Logger logger
    ) {
        this.workerExecutor = workerExecutor;
        this.registry = registry;
        this.configLoader = configLoader;
        this.messageRegistry = messageRegistry;
        this.messageConfigLoader = messageConfigLoader;
        this.intervalScheduleService = intervalScheduleService;
        this.clockScheduleService = clockScheduleService;
        this.timedDispatchQueue = timedDispatchQueue;
        this.locationAnnouncementService = locationAnnouncementService;
        this.firstJoinAnnouncementService = firstJoinAnnouncementService;
        this.dispatcher = dispatcher;
        this.dedupeService = dedupeService;
        this.logger = logger;
    }

    public CompletableFuture<Boolean> reloadAsync(CommandSender requester, boolean initialLoad) {
        if (shuttingDown.get()) {
            return CompletableFuture.completedFuture(false);
        }
        long generation = reloadGeneration.incrementAndGet();

        /*
         * Async boundary: config IO, message IO, YAML parsing, validation,
         * immutable snapshot creation and rescheduling all run on workerExecutor.
         * No Bukkit Player/World/Entity APIs are touched here.
         */
        return CompletableFuture.supplyAsync(this::loadReloadBundle, workerExecutor)
            .thenApplyAsync(bundle -> {
                if (shuttingDown.get() || generation != reloadGeneration.get()) {
                    return false;
                }
                timedDispatchQueue.invalidateAll();
                firstJoinAnnouncementService.cancelAll();
                registry.replace(bundle.configSnapshot());
                messageRegistry.replace(bundle.messageSnapshot());
                dedupeService.apply(bundle.configSnapshot().databaseSettings());
                intervalScheduleService.reschedule(bundle.configSnapshot());
                clockScheduleService.reschedule(bundle.configSnapshot());
                locationAnnouncementService.rebuildIndex();
                locationAnnouncementService.resetRuntimeState();
                logger.info("Configuration and message snapshots applied atomically.");
                if (!initialLoad) {
                    dispatcher.sendConfiguredMessage(requester, "service.reload-success");
                }
                return true;
            }, workerExecutor)
            .exceptionally(throwable -> {
                Throwable root = unwrap(throwable);
                if (root instanceof ConfigLoadException) {
                    logger.warning("Reload failed; keeping previous snapshots: " + root.getMessage());
                } else {
                    logger.log(Level.SEVERE, "Reload failed; keeping previous snapshots.", root);
                }
                if (!initialLoad) {
                    dispatcher.sendConfiguredMessage(requester, "service.reload-failed");
                }
                return false;
            });
    }

    public void shutdown() {
        shuttingDown.set(true);
        reloadGeneration.incrementAndGet();
        timedDispatchQueue.invalidateAll();
        intervalScheduleService.cancelAll();
        clockScheduleService.cancelAll();
        firstJoinAnnouncementService.cancelAll();
        locationAnnouncementService.resetRuntimeState();
        dedupeService.close();
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private ReloadBundle loadReloadBundle() {
        ConfigSnapshot configSnapshot = configLoader.load();
        MessageSnapshot messageSnapshot = messageConfigLoader.load();
        return new ReloadBundle(configSnapshot, messageSnapshot);
    }

    private record ReloadBundle(ConfigSnapshot configSnapshot, MessageSnapshot messageSnapshot) {
    }
}
