package qouteall.mini_scaled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.function.Function;
import java.util.function.Predicate;

public class ScaleBoxEntranceCreation {
    
    public static Item creationItem;
    
    public static Item getCreationItem() {
        if (creationItem == null) {
            return Items.NETHERITE_INGOT;
        }
        return creationItem;
    }
    
    public static void init() {
    
    }
    
    @Nullable
    private static IntBox matchAlongPath(
        BlockPos.MutableBlockPos currentPos,
        Direction direction,
        Predicate<BlockPos> pathPredicate,
        Function<BlockPos, IntBox> matchingFunc
    ) {
        for (int i = 0; i < ScaleBoxManipulation.MAX_INNER_LEN; i++) {
            IntBox box = matchingFunc.apply(currentPos);
            
            if (box != null) {
                return box;
            }
            
            currentPos.move(direction);
            
            if (pathPredicate.test(currentPos)) {
                continue;
            }
            else {
                // out of path. move back
                currentPos.move(direction.getOpposite());
                return null;
            }
        }
        return null;
    }
    
}
