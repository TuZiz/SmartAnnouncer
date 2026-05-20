package ym.smartannouncer.platform;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface PlatformScheduler {
    static PlatformScheduler disabled(PlatformCapabilities capabilities) {
        return new PlatformScheduler() {
            @Override
            public PlatformCapabilities capabilities() {
                return capabilities;
            }

            @Override
            public TaskHandle runGlobal(Runnable task) {
                return TaskHandle.NOOP;
            }

            @Override
            public TaskHandle runRegion(Location location, Runnable task) {
                return TaskHandle.NOOP;
            }

            @Override
            public TaskHandle runEntity(Entity entity, Runnable task) {
                return TaskHandle.NOOP;
            }

            @Override
            public TaskHandle runAsync(Runnable task) {
                return TaskHandle.NOOP;
            }

            @Override
            public void cancelAll() {
            }
        };
    }

    PlatformCapabilities capabilities();

    /*
     * Spigot: main server thread. Folia: global region scheduler. Do not use for
     * Player/Entity operations; those must go through runEntity.
     */
    TaskHandle runGlobal(Runnable task);

    /*
     * Folia location-owned work. The task must not operate on entities directly;
     * bridge entity operations through runEntity from inside this task.
     */
    TaskHandle runRegion(Location location, Runnable task);

    /*
     * Entity-owned work. Player messages, permission checks and location reads
     * enter through this method on Folia.
     */
    TaskHandle runEntity(Entity entity, Runnable task);

    /*
     * Pure async work only. Do not touch Bukkit World, Player, Entity or Chunk
     * state inside this task.
     */
    TaskHandle runAsync(Runnable task);

    void cancelAll();
}
