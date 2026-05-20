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
}
