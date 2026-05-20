package ym.smartannouncer.config.model;

public record SphereRegionShape(
    String worldName,
    double centerX,
    double centerY,
    double centerZ,
    double radius
) implements RegionShape {
    public SphereRegionShape {
        if (radius <= 0) {
            throw new IllegalArgumentException("Sphere radius must be greater than 0.");
        }
    }

    @Override
    public RegionKind kind() {
        return RegionKind.SPHERE;
    }

    @Override
    public boolean contains(String worldName, double x, double y, double z) {
        if (!this.worldName.equals(worldName)) {
            return false;
        }
        double dx = x - centerX;
        double dy = y - centerY;
        double dz = z - centerZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    @Override
    public boolean intersectsBlock(int blockX, int blockY, int blockZ) {
        double closestX = clamp(centerX, blockX, blockX + 1.0D);
        double closestY = clamp(centerY, blockY, blockY + 1.0D);
        double closestZ = clamp(centerZ, blockZ, blockZ + 1.0D);
        double dx = closestX - centerX;
        double dy = closestY - centerY;
        double dz = closestZ - centerZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
