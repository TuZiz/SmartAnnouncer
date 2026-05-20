package ym.smartannouncer.config.model;

public interface RegionShape {
    RegionKind kind();

    String worldName();

    boolean contains(String worldName, double x, double y, double z);

    boolean intersectsBlock(int blockX, int blockY, int blockZ);

    double centerX();

    double centerY();

    double centerZ();
}
