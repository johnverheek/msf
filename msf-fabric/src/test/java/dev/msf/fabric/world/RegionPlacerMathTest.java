package dev.msf.fabric.world;

/**
 * Rotation-math tests for {@link RegionPlacer}.
 *
 * <p>{@link RegionPlacer#computeRotation} and {@link RegionPlacer#rotatePosition}
 * both take/return {@code net.minecraft.util.BlockRotation}, which is a Minecraft
 * type only available at game runtime. These tests therefore require Fabric Loom's
 * gametest infrastructure and cannot run via plain JUnit.
 *
 * <p>The rotation-delta formula (the pure-Java part of {@code computeRotation})
 * is covered without Minecraft by {@link CanonicalFacingTest}.
 *
 * <p>Test cases to implement as Fabric Gametests:
 * <ol>
 *   <li><b>rotatePosition_none_isIdentity</b> — {@code rotatePosition(3,7,NONE)} → {@code [3,7]}.</li>
 *   <li><b>rotatePosition_cw90</b> — {@code rotatePosition(5,2,CW_90)} → {@code [-2,5]}
 *       (formula: {@code x'=-z, z'=x}).</li>
 *   <li><b>rotatePosition_cw180</b> — {@code rotatePosition(5,2,CW_180)} → {@code [-5,-2]}.</li>
 *   <li><b>rotatePosition_ccw90</b> — {@code rotatePosition(5,2,CCW_90)} → {@code [2,-5]}.</li>
 *   <li><b>fourCw90IsIdentity</b> — applying CW_90 four times returns the original position.</li>
 *   <li><b>twoCw90EqualsCw180</b> — two successive CW_90 equals one CW_180.</li>
 *   <li><b>computeRotation_northToEast_isCw90</b> — assert {@code CLOCKWISE_90}.</li>
 *   <li><b>computeRotation_northToSouth_isCw180</b> — assert {@code CLOCKWISE_180}.</li>
 *   <li><b>computeRotation_northToWest_isCcw90</b> — assert {@code COUNTERCLOCKWISE_90}.</li>
 *   <li><b>computeRotation_same_isNone</b> — assert {@code NONE} for all four same-direction pairs.</li>
 * </ol>
 *
 * <p>Run with: {@code ./gradlew runGametest}
 */
public class RegionPlacerMathTest {
    // Gametest implementations go in the testmod source set once Fabric Loom is configured.
}
