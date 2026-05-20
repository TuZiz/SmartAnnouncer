package ym.smartannouncer.config.model;

public record CuboidRegionShape(
    String worldName,
    double minX,
    double minY,
    double minZ,
    double maxX,
    double maxY,
    double maxZ
) implements RegionShape {
    public CuboidRegionShape {
        double lowX = Math.min(minX, maxX);
        double lowY = Math.min(minY, maxY);
        double lowZ = Math.min(minZ, maxZ);
        double highX = Math.max(minX, maxX);
        double highY = Math.max(minY, maxY);
        double highZ = Math.max(minZ, maxZ);
        minX = lowX;
        minY = lowY;
        minZ = lowZ;
        maxX = highX;
        maxY = highY;
        maxZ = highZ;
    }

    @Override
    public RegionKind kind() {
        return RegionKind.CUBOID;
    }

    @Override
    public boolean contains(String worldName, double x, double y, double z) {
        return this.worldName.equals(worldName)
            && x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    @Override
    public double centerX() {
        return (minX + maxX) / 2.0D;
    }

    @Override
    public double centerY() {
        return (minY + maxY) / 2.0D;
    }

    @Override
    public double centerZ() {
        return (minZ + maxZ) / 2.0D;
    }
}
