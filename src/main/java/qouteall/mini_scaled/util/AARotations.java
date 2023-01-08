package qouteall.mini_scaled.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.DiligentMatcher;

/**
 * Axis-Aligned Rotations
 */
public enum AARotations {
    
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
    
    private final Direction transformedX;
    private final Direction transformedY;
    private final Direction transformedZ;
    private final DiligentMatcher.IntMatrix3 matrix;
    
    
    AARotations(Direction transformedZ, Direction transformedX) {
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
    
    private static final AARotations[][] multiplicationCache = new AARotations[12][12];
    
    static {
        for (AARotations a : values()) {
            for (AARotations b : values()) {
                multiplicationCache[a.ordinal()][b.ordinal()] = a.rawMultiply(b);
            }
        }
    }
    
    // firstly apply other, then apply this
    public AARotations multiply(AARotations other) {
        return multiplicationCache[this.ordinal()][other.ordinal()];
    }
    
    // firstly apply other, then apply this
    private AARotations rawMultiply(AARotations other) {
        return getAARotationFromZX(
            transformDirection(other.transformedZ),
            transformDirection(other.transformedX)
        );
    }
    
    private static AARotations getAARotationFromZX(Direction transformedZ, Direction transformedX) {
        for (AARotations value : values()) {
            if (value.transformedZ == transformedZ && value.transformedX == transformedX) {
                return value;
            }
        }
        throw new IllegalArgumentException();
    }
    
}
