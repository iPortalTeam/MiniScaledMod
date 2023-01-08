package qouteall.mini_scaled.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.DirectionTransformation;
import net.minecraft.util.math.Vec3i;
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.DiligentMatcher;

import java.util.Arrays;

/**
 * Axis-Aligned Rotations.
 * Vanilla's {@link DirectionTransformation} contains the mirrored transformations. This only contain rotations.
 */
public enum AARotation {
    
    SOUTH_ROT0(Direction.SOUTH, Direction.EAST),
    SOUTH_ROT90(Direction.SOUTH, Direction.UP),
    SOUTH_ROT180(Direction.SOUTH, Direction.WEST),
    SOUTH_ROT270(Direction.SOUTH, Direction.DOWN),
    
    NORTH_ROT0(Direction.NORTH, Direction.WEST),
    NORTH_ROT90(Direction.NORTH, Direction.UP),
    NORTH_ROT180(Direction.NORTH, Direction.EAST),
    NORTH_ROT270(Direction.NORTH, Direction.DOWN),
    
    EAST_ROT0(Direction.EAST, Direction.NORTH),
    EAST_ROT90(Direction.EAST, Direction.UP),
    EAST_ROT180(Direction.EAST, Direction.SOUTH),
    EAST_ROT270(Direction.EAST, Direction.DOWN),
    
    WEST_ROT0(Direction.WEST, Direction.SOUTH),
    WEST_ROT90(Direction.WEST, Direction.UP),
    WEST_ROT180(Direction.WEST, Direction.NORTH),
    WEST_ROT270(Direction.WEST, Direction.DOWN),
    
    UP_ROT0(Direction.UP, Direction.NORTH),
    UP_ROT90(Direction.UP, Direction.WEST),
    UP_ROT180(Direction.UP, Direction.SOUTH),
    UP_ROT270(Direction.UP, Direction.EAST),
    
    DOWN_ROT0(Direction.DOWN, Direction.SOUTH),
    DOWN_ROT90(Direction.DOWN, Direction.WEST),
    DOWN_ROT180(Direction.DOWN, Direction.NORTH),
    DOWN_ROT270(Direction.DOWN, Direction.EAST);
    
    public static final AARotation IDENTITY = SOUTH_ROT0;
    
    private final Direction transformedX;
    private final Direction transformedY;
    private final Direction transformedZ;
    private final DiligentMatcher.IntMatrix3 matrix;
    
    
    AARotation(Direction transformedZ, Direction transformedX) {
        this.transformedZ = transformedZ;
        this.transformedX = transformedX;
        this.transformedY = dirCrossProduct(transformedZ, transformedX);
        matrix = new DiligentMatcher.IntMatrix3(
            this.transformedX.getVector(),
            this.transformedY.getVector(),
            this.transformedZ.getVector()
        );
    }
    
    public BlockPos transform(Vec3i vec) {
        return matrix.transform(vec);
    }
    
    public Direction transformDirection(Direction direction) {
        return Direction.fromVector(transform(direction.getVector()));
    }
    
    public static Direction dirCrossProduct(Direction a, Direction b) {
        return Direction.fromVector(
            a.getOffsetY() * b.getOffsetZ() - a.getOffsetZ() * b.getOffsetY(),
            a.getOffsetZ() * b.getOffsetX() - a.getOffsetX() * b.getOffsetZ(),
            a.getOffsetX() * b.getOffsetY() - a.getOffsetY() * b.getOffsetX()
        );
    }
    
    private static final AARotation[][] multiplicationCache = new AARotation[24][24];
    
    static {
        for (AARotation a : values()) {
            for (AARotation b : values()) {
                multiplicationCache[a.ordinal()][b.ordinal()] = a.rawMultiply(b);
            }
        }
    }
    
    // firstly apply other, then apply this
    public AARotation multiply(AARotation other) {
        return multiplicationCache[this.ordinal()][other.ordinal()];
    }
    
    // firstly apply other, then apply this
    private AARotation rawMultiply(AARotation other) {
        return getAARotationFromZX(
            transformDirection(other.transformedZ),
            transformDirection(other.transformedX)
        );
    }
    
    private static AARotation getAARotationFromZX(Direction transformedZ, Direction transformedX) {
        for (AARotation value : values()) {
            if (value.transformedZ == transformedZ && value.transformedX == transformedX) {
                return value;
            }
        }
        throw new IllegalArgumentException();
    }
    
    private static final AARotation[] inverseCache = new AARotation[24];
    
    static {
        AARotation[] values = values();
        for (int i = 0; i < values.length; i++) {
            AARotation rot = values[i];
            // brute force inverse
            AARotation inverse = Arrays.stream(values())
                .filter(b -> rot.multiply(b) == IDENTITY).findFirst().orElseThrow();
            inverseCache[i] = inverse;
        }
    }
    
    public AARotation getInverse() {
        return inverseCache[this.ordinal()];
    }
}
