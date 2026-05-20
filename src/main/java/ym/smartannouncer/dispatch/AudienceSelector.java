package ym.smartannouncer.dispatch;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import ym.smartannouncer.config.model.AnnouncementDefinition;
import ym.smartannouncer.util.LocationPoint;

public final class AudienceSelector {
    /*
     * Entity-thread-only method. It reads Player online/world/permission state
     * and must only be called inside PlatformScheduler#runEntity.
     */
    public boolean canReceive(Player player, AnnouncementDefinition announcement) {
        if (!player.isOnline()) {
            return false;
        }
        if (!announcement.worlds().isEmpty()) {
            World world = player.getWorld();
            if (world == null || !announcement.worlds().contains(world.getName())) {
                return false;
            }
        }
        String permission = announcement.permission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    /*
     * Entity-thread-only method. Player#getLocation is entity-owned on Folia and
     * is never called from async/global/region code.
     */
    public boolean isNear(Player player, LocationPoint point, double radius) {
        Location location = player.getLocation();
        if (location.getWorld() == null || !location.getWorld().getName().equals(point.worldName())) {
            return false;
        }
        double dx = location.getX() - point.x();
        double dy = location.getY() - point.y();
        double dz = location.getZ() - point.z();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
