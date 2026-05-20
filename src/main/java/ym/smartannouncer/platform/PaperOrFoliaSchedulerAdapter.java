package ym.smartannouncer.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class PaperOrFoliaSchedulerAdapter implements PlatformScheduler {
    private final JavaPlugin plugin;
    private final PlatformCapabilities capabilities;
    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;
    private final Method globalExecute;
    private final Method globalCancelTasks;
    private final Method regionExecute;
    private final Method asyncRunNow;
    private final Method asyncCancelTasks;
    private final Method entityGetScheduler;
    private final ConcurrentMap<Class<?>, Method> entityExecuteMethods = new ConcurrentHashMap<>();

    public PaperOrFoliaSchedulerAdapter(JavaPlugin plugin, PlatformCapabilities capabilities) throws ReflectiveOperationException {
        this.plugin = plugin;
        this.capabilities = capabilities;
        this.globalScheduler = invokeStatic(Bukkit.class.getMethod("getGlobalRegionScheduler"));
        this.regionScheduler = invokeStatic(Bukkit.class.getMethod("getRegionScheduler"));
        this.asyncScheduler = invokeStatic(Bukkit.class.getMethod("getAsyncScheduler"));
        this.globalExecute = publicSchedulerMethod("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler",
            globalScheduler, "execute", Plugin.class, Runnable.class);
        this.globalCancelTasks = publicSchedulerMethod("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler",
            globalScheduler, "cancelTasks", Plugin.class);
        this.regionExecute = publicSchedulerMethod("io.papermc.paper.threadedregions.scheduler.RegionScheduler",
            regionScheduler, "execute", Plugin.class, Location.class, Runnable.class);
        this.asyncRunNow = publicSchedulerMethod("io.papermc.paper.threadedregions.scheduler.AsyncScheduler",
            asyncScheduler, "runNow", Plugin.class, Consumer.class);
        this.asyncCancelTasks = publicSchedulerMethod("io.papermc.paper.threadedregions.scheduler.AsyncScheduler",
            asyncScheduler, "cancelTasks", Plugin.class);
        this.entityGetScheduler = accessible(Entity.class.getMethod("getScheduler"));
    }

    @Override
    public PlatformCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public TaskHandle runGlobal(Runnable task) {
        try {
            globalExecute.invoke(globalScheduler, plugin, guard(task));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to schedule global task.", ex);
        }
        return TaskHandle.NOOP;
    }

    @Override
    public TaskHandle runRegion(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Cannot schedule region task without a loaded world location.");
            return TaskHandle.NOOP;
        }
        try {
            regionExecute.invoke(regionScheduler, plugin, location, guard(task));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to schedule region task.", ex);
        }
        return TaskHandle.NOOP;
    }

    @Override
    public TaskHandle runEntity(Entity entity, Runnable task) {
        if (entity == null) {
            return TaskHandle.NOOP;
        }
        try {
            Object entityScheduler = entityGetScheduler.invoke(entity);
            Method execute = entityExecuteMethods.computeIfAbsent(entityScheduler.getClass(), this::findEntityExecuteMethod);
            Object accepted = execute.invoke(entityScheduler, plugin, guard(task), null, 1L);
            if (accepted instanceof Boolean scheduled && !scheduled) {
                plugin.getLogger().fine("Entity task was rejected because the entity scheduler is retired.");
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to schedule entity task.", ex);
        }
        return TaskHandle.NOOP;
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        try {
            Consumer<Object> consumer = ignored -> guard(task).run();
            Object scheduledTask = asyncRunNow.invoke(asyncScheduler, plugin, consumer);
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to schedule async task.", ex);
            return TaskHandle.NOOP;
        }
    }

    @Override
    public void cancelAll() {
        try {
            globalCancelTasks.invoke(globalScheduler, plugin);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to cancel global tasks.", ex);
        }
        try {
            asyncCancelTasks.invoke(asyncScheduler, plugin);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to cancel async tasks.", ex);
        }
    }

    private Method findEntityExecuteMethod(Class<?> schedulerClass) {
        try {
            return publicSchedulerMethod("io.papermc.paper.threadedregions.scheduler.EntityScheduler",
                schedulerClass, "execute", Plugin.class, Runnable.class, Runnable.class, long.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException("EntityScheduler#execute method is not available.", ex);
        }
    }

    private static Object invokeStatic(Method method) throws ReflectiveOperationException {
        accessible(method);
        return method.invoke(null);
    }

    private static Method publicSchedulerMethod(String apiClassName, Object scheduler, String name, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        return publicSchedulerMethod(apiClassName, scheduler.getClass(), name, parameterTypes);
    }

    private static Method publicSchedulerMethod(String apiClassName, Class<?> fallbackClass, String name, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        try {
            return accessible(Class.forName(apiClassName).getMethod(name, parameterTypes));
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return accessible(fallbackClass.getMethod(name, parameterTypes));
        }
    }

    private static <T extends AccessibleObject> T accessible(T object) {
        try {
            object.setAccessible(true);
        } catch (RuntimeException ignored) {
            // Public API members do not require accessibility changes. The call
            // is still attempted to protect against package-private impl classes.
        }
        return object;
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

    private static final class ReflectiveTaskHandle implements TaskHandle {
        private final Object task;
        private final Method cancelMethod;

        private ReflectiveTaskHandle(Object task) {
            this.task = task;
            this.cancelMethod = findNoArgMethod(task, "cancel");
        }

        @Override
        public void cancel() {
            if (cancelMethod == null) {
                return;
            }
            try {
                cancelMethod.invoke(task);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public boolean isCancelled() {
            Method method = findNoArgMethod(task, "isCancelled");
            if (method == null) {
                return false;
            }
            try {
                Object value = method.invoke(task);
                return value instanceof Boolean cancelled && cancelled;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }

        private static Method findNoArgMethod(Object instance, String name) {
            if (instance == null) {
                return null;
            }
            try {
                return accessible(instance.getClass().getMethod(name));
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }
}
