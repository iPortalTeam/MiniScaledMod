package qouteall.mini_scaled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.Arrays;
import java.util.function.Predicate;

public class GlassFrameMatching {
    public static @Nullable IntBox matchGlassFrame(
        Level world,
        BlockPos startingPos,
        Predicate<BlockPos> predicate,
        int maxLen
    ) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        
        mutable.set(startingPos);
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction dir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
            matchFurthest(mutable, dir, predicate, maxLen);
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction dir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
            matchFurthest(mutable, dir, predicate, maxLen);
        }
        
        BlockPos lowEnd = mutable.immutable();
        
        mutable.set(startingPos);
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction dir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            matchFurthest(mutable, dir, predicate, maxLen);
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction dir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            matchFurthest(mutable, dir, predicate, maxLen);
        }
        
        BlockPos highEnd = mutable.immutable();
        
        IntBox box = new IntBox(lowEnd, highEnd);
        
        boolean boxMatches = Arrays.stream(box.get12Edges())
            .allMatch(edge -> edge.fastStream().allMatch(predicate));
        
        if (boxMatches) {
            return box;
        }
        else {
            return null;
        }
    }
    
    /**
     * This will mutate the block pos
     */
    public static int matchFurthest(
        BlockPos.MutableBlockPos mutable,
        Direction direction,
        Predicate<BlockPos> predicate,
        int maxLen
    ) {
        int offset = 0;
        for (; ; ) {
            mutable.move(direction);
            offset += 1;
            if (!predicate.test(mutable) || offset > maxLen) {
                mutable.move(direction.getOpposite());
                offset -= 1;
                return offset;
            }
        }
    }
    
    public static BlockPos getFurthest(
        BlockPos pos,
        Direction direction,
        Predicate<BlockPos> predicate
    ) {
        BlockPos current = pos;
        for (int i = 1; i < 64; i++) {
            BlockPos newPos = pos.offset(direction.getNormal().multiply(i));
            if (predicate.test(newPos)) {
                current = newPos;
            }
            else {
                return current;
            }
        }
        return current;
    }
    
    public static int getFurthestLen(
        BlockPos pos,
        Direction direction,
        Predicate<BlockPos> predicate
    ) {
        for (int i = 1; i < 64; i++) {
            BlockPos newPos = pos.offset(direction.getNormal().multiply(i));
            if (!predicate.test(newPos)) {
                return i;
            }
        }
        return 64;
    }
}
