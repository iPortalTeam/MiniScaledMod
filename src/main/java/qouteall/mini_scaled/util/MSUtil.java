package qouteall.mini_scaled.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;

import java.util.function.Predicate;

public class MSUtil {
    public static int countItem(
        Container container,
        Predicate<ItemStack> predicate
    ) {
        int containerSize = container.getContainerSize();
        int sum = 0;
        for (int i = 0; i < containerSize; i++) {
            ItemStack itemStack = container.getItem(i);
            if (predicate.test(itemStack)) {
                sum += itemStack.getCount();
            }
        }
        return sum;
    }
    
    /**
     * @return the count of missing items to remove
     */
    public static int removeItem(
        Container container,
        Predicate<ItemStack> predicate,
        int removeCount
    ) {
        int toRemove = removeCount;
        
        int containerSize = container.getContainerSize();
        for (int i = 0; i < containerSize; i++) {
            ItemStack itemStack = container.getItem(i);
            if (predicate.test(itemStack)) {
                int removed = Math.min(toRemove, itemStack.getCount());
                itemStack.shrink(removed);
                toRemove -= removed;
                if (toRemove == 0) {
                    break;
                }
            }
        }
        return toRemove;
    }
    
    public static boolean removeIfHas(
        Container container,
        Predicate<ItemStack> predicate,
        int requiredNum
    ) {
        int count = countItem(container, predicate);
        if (count >= requiredNum) {
            removeItem(container, predicate, requiredNum);
            return true;
        }
        else {
            return false;
        }
    }
    
    public static Vec3 getGravityVec(Entity entity) {
        Direction gravity = GravityChangerInterface.invoker.getGravityDirection(entity);
        Vec3 gravityVec = Vec3.atLowerCornerOf(gravity.getNormal());
        return gravityVec;
    }
    
    public static BlockPos getSpawnPos(Level world) {
        return world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, world.getSharedSpawnPos());
    }
}
