package ym.smartannouncer.util;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class ThreadUtil {
    private ThreadUtil() {
    }

    public static void shutdown(ExecutorService executorService, Logger logger, String name) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(3L, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executorService.shutdownNow();
                logger.warning("Forced shutdown of " + name + "; dropped " + dropped.size() + " queued tasks.");
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warning("Interrupted while shutting down " + name + ".");
        }
    }
}
