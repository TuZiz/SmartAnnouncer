package ym.smartannouncer.util;

import org.bukkit.Location;

public record LocationPoint(String worldName, double x, double y, double z) {
    public static LocationPoint from(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location world is not loaded.");
        }
        return new LocationPoint(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }
}
