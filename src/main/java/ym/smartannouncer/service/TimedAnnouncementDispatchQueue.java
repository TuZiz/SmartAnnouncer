package ym.smartannouncer.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class TimedAnnouncementDispatchQueue {
    private final long minimumGapMillis;
    private final ScheduledExecutorService workerExecutor;
    private final AtomicLong queueCursorMillis = new AtomicLong();
    private final AtomicLong generation = new AtomicLong();
    private final Set<ScheduledFuture<?>> pendingTasks = ConcurrentHashMap.newKeySet();

    public TimedAnnouncementDispatchQueue(ScheduledExecutorService workerExecutor, long minimumGapMillis) {
        this.workerExecutor = workerExecutor;
        this.minimumGapMillis = minimumGapMillis;
    }

    /*
     * Async queue boundary: reserve a future dispatch slot so scheduled
     * announcements that hit the same second are sent with a small gap.
     * The runnable itself must still avoid Bukkit API unless it later re-enters
     * entity/global/region-safe scheduling.
     */
    public void enqueue(Runnable task) {
        long currentGeneration = generation.get();
        long delayMillis = reserveDispatchDelayMillis();
        ScheduledFuture<?> future = workerExecutor.schedule(() -> {
            try {
                if (currentGeneration == generation.get()) {
                    task.run();
                }
            } finally {
                pendingTasks.removeIf(ScheduledFuture::isDone);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        pendingTasks.add(future);
    }

    public void invalidateAll() {
        generation.incrementAndGet();
        queueCursorMillis.set(0L);
        for (ScheduledFuture<?> task : pendingTasks) {
            task.cancel(false);
        }
        pendingTasks.clear();
    }

    private long reserveDispatchDelayMillis() {
        long now = System.currentTimeMillis();
        while (true) {
            long previous = queueCursorMillis.get();
            long slot = Math.max(now, previous);
            long next = slot + minimumGapMillis;
            if (queueCursorMillis.compareAndSet(previous, next)) {
                return Math.max(0L, slot - now);
            }
        }
    }
}
