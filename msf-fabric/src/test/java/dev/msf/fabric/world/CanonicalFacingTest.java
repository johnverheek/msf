package dev.msf.fabric.world;

import dev.msf.core.model.MsfMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Standalone JUnit tests for {@link CanonicalFacing} and the rotation-delta
 * formula used by {@link RegionPlacer#computeRotation}.
 *
 * <p>No Minecraft environment required — run with
 * {@code ./gradlew :msf-fabric:test}.
 */
class CanonicalFacingTest {

    // =========================================================================
    // CanonicalFacing values
    // =========================================================================

    @Test
    void msfValuesMatchMetadataConstants() {
        assertEquals(MsfMetadata.FACING_NORTH, CanonicalFacing.NORTH.msfValue());
        assertEquals(MsfMetadata.FACING_SOUTH, CanonicalFacing.SOUTH.msfValue());
        assertEquals(MsfMetadata.FACING_EAST,  CanonicalFacing.EAST.msfValue());
        assertEquals(MsfMetadata.FACING_WEST,  CanonicalFacing.WEST.msfValue());
    }

    @Test
    void fromMsfValueRoundTrips() {
        for (CanonicalFacing f : CanonicalFacing.values()) {
            assertSame(f, CanonicalFacing.fromMsfValue(f.msfValue()),
                "fromMsfValue must round-trip for " + f);
        }
    }

    @Test
    void fromMsfValueRejectsInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> CanonicalFacing.fromMsfValue(4));
        assertThrows(IllegalArgumentException.class, () -> CanonicalFacing.fromMsfValue(-1));
    }

    @Test
    void cwOrdinalIsDistinctAndInRange() {
        int[] ordinals = {
            CanonicalFacing.NORTH.cwOrdinal(),
            CanonicalFacing.EAST.cwOrdinal(),
            CanonicalFacing.SOUTH.cwOrdinal(),
            CanonicalFacing.WEST.cwOrdinal(),
        };
        for (int i = 0; i < ordinals.length; i++) {
            assertTrue(ordinals[i] >= 0 && ordinals[i] <= 3,
                "cwOrdinal must be in [0, 3], got " + ordinals[i]);
            for (int j = i + 1; j < ordinals.length; j++) {
                assertNotEquals(ordinals[i], ordinals[j],
                    "cwOrdinal must be distinct for each facing");
            }
        }
    }

    @Test
    void cwOrdinalNorthIsZero() {
        assertEquals(0, CanonicalFacing.NORTH.cwOrdinal());
    }

    @Test
    void cwOrdinalEastIsOne() {
        assertEquals(1, CanonicalFacing.EAST.cwOrdinal());
    }

    // =========================================================================
    // Rotation delta formula — tests the pure-Java part of computeRotation
    // without needing net.minecraft.util.BlockRotation on the classpath.
    //
    // Formula: delta = (target.cwOrdinal() - canonical.cwOrdinal() + 4) % 4
    // Maps to BlockRotation.values()[delta] in RegionPlacer.computeRotation().
    // =========================================================================

    private static int delta(CanonicalFacing canonical, CanonicalFacing target) {
        return (target.cwOrdinal() - canonical.cwOrdinal() + 4) % 4;
    }

    @Test
    void rotationDelta_sameDirection_isZero() {
        for (CanonicalFacing f : CanonicalFacing.values()) {
            assertEquals(0, delta(f, f), "Same direction must produce delta 0 for " + f);
        }
    }

    @Test
    void rotationDelta_northToEast_isOne() {
        assertEquals(1, delta(CanonicalFacing.NORTH, CanonicalFacing.EAST));
    }

    @Test
    void rotationDelta_northToSouth_isTwo() {
        assertEquals(2, delta(CanonicalFacing.NORTH, CanonicalFacing.SOUTH));
    }

    @Test
    void rotationDelta_northToWest_isThree() {
        assertEquals(3, delta(CanonicalFacing.NORTH, CanonicalFacing.WEST));
    }

    @Test
    void rotationDelta_eastToNorth_isThree() {
        assertEquals(3, delta(CanonicalFacing.EAST, CanonicalFacing.NORTH));
    }

    @Test
    void rotationDelta_eastToSouth_isOne() {
        assertEquals(1, delta(CanonicalFacing.EAST, CanonicalFacing.SOUTH));
    }

    @Test
    void rotationDelta_alwaysInRangeZeroToThree() {
        for (CanonicalFacing from : CanonicalFacing.values()) {
            for (CanonicalFacing to : CanonicalFacing.values()) {
                int d = delta(from, to);
                assertTrue(d >= 0 && d <= 3,
                    "Delta must be in [0,3], got " + d + " for " + from + " → " + to);
            }
        }
    }
}
