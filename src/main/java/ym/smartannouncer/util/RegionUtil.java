package ym.smartannouncer.util;

import org.bukkit.Location;
import ym.smartannouncer.config.model.RegionShape;

public final class RegionUtil {
    private RegionUtil() {
    }

    public static boolean changedBlock(Location from, Location to) {
        if (from == null || to == null) {
            return true;
        }
        String fromWorld = from.getWorld() == null ? "" : from.getWorld().getName();
        String toWorld = to.getWorld() == null ? "" : to.getWorld().getName();
        return !fromWorld.equals(toWorld)
            || from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ();
    }

    public static boolean contains(RegionShape shape, LocationPoint point) {
        return shape.contains(point.worldName(), point.x(), point.y(), point.z());
    }
}
