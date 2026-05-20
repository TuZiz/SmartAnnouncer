package ym.smartannouncer.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class PlatformDetector {
    private PlatformDetector() {
    }

    public static PlatformScheduler create(JavaPlugin plugin) {
        boolean paperPresent = hasClass("com.destroystokyo.paper.PaperConfig")
            || hasClass("io.papermc.paper.configuration.Configuration");
        boolean foliaPresent = hasClass("io.papermc.paper.threadedregions.RegionizedServer");
        boolean global = hasBukkitNoArgMethod("getGlobalRegionScheduler");
        boolean region = hasBukkitNoArgMethod("getRegionScheduler");
        boolean async = hasBukkitNoArgMethod("getAsyncScheduler");
        boolean entity = hasEntitySchedulerMethod();

        PlatformCapabilities capabilities = new PlatformCapabilities(
            paperPresent,
            foliaPresent,
            global,
            region,
            entity,
            async,
            global && region && entity ? "Paper/Folia reflective scheduler" : "Spigot BukkitScheduler fallback"
        );

        if (global && region && entity) {
            try {
                return new PaperOrFoliaSchedulerAdapter(plugin, capabilities);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "Paper/Folia scheduler reflection failed; falling back to BukkitScheduler.", ex);
            }
        }
        return new SpigotSchedulerAdapter(plugin, capabilities);
    }

    private static boolean hasClass(String className) {
        try {
            Class.forName(className, false, PlatformDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean hasBukkitNoArgMethod(String methodName) {
        try {
            Method method = Bukkit.class.getMethod(methodName);
            return method.getParameterCount() == 0;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean hasEntitySchedulerMethod() {
        try {
            Entity.class.getMethod("getScheduler");
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
