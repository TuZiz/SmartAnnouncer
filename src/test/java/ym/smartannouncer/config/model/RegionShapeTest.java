package ym.smartannouncer.config.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionShapeTest {
    @Test
    void cuboidBlockIntersectionUsesBoundingBox() {
        CuboidRegionShape shape = new CuboidRegionShape("world", 10, 64, 10, 12, 66, 12);

        assertTrue(shape.intersectsBlock(10, 64, 10));
        assertTrue(shape.intersectsBlock(12, 66, 12));
        assertFalse(shape.intersectsBlock(13, 64, 10));
    }

    @Test
    void sphereBlockIntersectionUsesClosestPoint() {
        SphereRegionShape shape = new SphereRegionShape("world", 0.5D, 64.5D, 0.5D, 1.0D);

        assertTrue(shape.intersectsBlock(0, 64, 0));
        assertTrue(shape.intersectsBlock(1, 64, 0));
        assertFalse(shape.intersectsBlock(3, 64, 0));
    }
}
