package qouteall.mini_scaled;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import qouteall.mini_scaled.item.ManipulationWandItem;
import qouteall.mini_scaled.item.ScaleBoxEntranceItem;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
