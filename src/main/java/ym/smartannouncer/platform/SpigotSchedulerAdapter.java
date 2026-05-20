package ym.smartannouncer.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

public final class SpigotSchedulerAdapter implements PlatformScheduler {
    private final JavaPlugin plugin;
    private final PlatformCapabilities capabilities;

    public SpigotSchedulerAdapter(JavaPlugin plugin, PlatformCapabilities capabilities) {
        this.plugin = plugin;
        this.capabilities = capabilities;
    }

    @Override
    public PlatformCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public TaskHandle runGlobal(Runnable task) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, guard(task));
        return new BukkitTaskHandle(bukkitTask);
    }

    @Override
    public TaskHandle runRegion(Location location, Runnable task) {
        return runGlobal(task);
    }

    @Override
    public TaskHandle runEntity(Entity entity, Runnable task) {
        return runGlobal(task);
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, guard(task));
        return new BukkitTaskHandle(bukkitTask);
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    private Runnable guard(Runnable task) {
        return () -> {
            if (!plugin.isEnabled()) {
                return;
            }
            try {
                task.run();
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.SEVERE, "Scheduled task failed.", throwable);
            }
        };
    }

    private static final class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask task;

        private BukkitTaskHandle(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
