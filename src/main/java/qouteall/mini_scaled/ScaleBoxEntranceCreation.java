package qouteall.mini_scaled;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.Arrays;
import java.util.function.Predicate;

public class ScaleBoxEntranceCreation {
    
    public static void init() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stackInHand = player.getStackInHand(hand);
            
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (stackInHand.getItem() == Items.NETHERITE_INGOT) {
                    boolean succeeded =
                        onRightClickBoxFrameUsingNetherite(serverPlayer, hitResult.getBlockPos());
                    if (succeeded) {
                        stackInHand.decrement(1);
                        return ActionResult.CONSUME;
                    }
                    else {
                        return ActionResult.FAIL;
                    }
                }
            }
            
            return ActionResult.PASS;
        });
    }
    
    private static boolean onRightClickBoxFrameUsingNetherite(
        ServerPlayerEntity player,
        BlockPos pos
    ) {
        ServerWorld world = (ServerWorld) player.world;
        
        BlockState originBlockState = world.getBlockState(pos);
        if (!(originBlockState.getBlock() instanceof StainedGlassBlock stainedGlassBlock)) {
            return false;
        }
        
        IntBox box = detectBoxFrame(world, pos, originBlockState);
        
        if (box == null) {
            return false;
        }
        
        Integer boxSize = getBoxSizeAndValidate(box);
        
        if (boxSize == null) {
            player.sendMessage(new TranslatableText(
                "mini_scaled.invalid_box_size",
                String.format("(%d,%d,%d)", box.getSize().getX(), box.getSize().getY(), box.getSize().getZ())
            ), false);
            return false;
        }
        
        // give player the item
        DyeColor color = stainedGlassBlock.getColor();
        ItemStack itemStack = new ItemStack(ScaleBoxEntranceItem.instance);
        ScaleBoxEntranceItem.ItemInfo itemInfo = new ScaleBoxEntranceItem.ItemInfo(boxSize, color);
        itemInfo.writeToTag(itemStack.getOrCreateNbt());
        player.giveItemStack(itemStack);
        
        // remove the frame
        for (IntBox edge : box.get12Edges()) {
            edge.fastStream().forEach(p -> {
                world.setBlockState(p, Blocks.AIR.getDefaultState());
            });
        }
        
        return true;
    }
    
    @Nullable
    private static Integer getBoxSizeAndValidate(IntBox box) {
        BlockPos size = box.getSize();
        for (int len : ScaleBoxGeneration.supportedScales) {
            if (size.getX() == len && size.getY() == len && size.getZ() == len) {
                return len;
            }
        }
        return null;
    }
    
    @Nullable
    private static IntBox detectBoxFrame(
        ServerWorld world,
        BlockPos pos,
        BlockState blockState
    ) {
        Predicate<BlockPos> predicate = p -> world.getBlockState(p) == blockState;
        
        pos = getFurthest(pos, Direction.DOWN, predicate);
        pos = getFurthest(pos, Direction.NORTH, predicate);
        pos = getFurthest(pos, Direction.WEST, predicate);
        pos = getFurthest(pos, Direction.DOWN, predicate);
        pos = getFurthest(pos, Direction.NORTH, predicate);
        pos = getFurthest(pos, Direction.WEST, predicate);
        
        BlockPos basePos = pos;
        
        int xLen = getFurthestLen(basePos, Direction.EAST, predicate);
        int yLen = getFurthestLen(basePos, Direction.UP, predicate);
        int zLen = getFurthestLen(basePos, Direction.SOUTH, predicate);
        
        IntBox box = IntBox.getBoxByBasePointAndSize(new BlockPos(xLen, yLen, zLen), basePos);
        
        boolean allGlasses = Arrays.stream(box.get12Edges()).allMatch(edge -> edge.fastStream().allMatch(predicate));
        
        if (!allGlasses) {
            return null;
        }
        
        return box;
    }
    
    private static BlockPos getFurthest(
        BlockPos pos,
        Direction direction,
        Predicate<BlockPos> predicate
    ) {
        BlockPos current = pos;
        for (int i = 1; i < 64; i++) {
            BlockPos newPos = pos.add(direction.getVector().multiply(i));
            if (predicate.test(newPos)) {
                current = newPos;
            }
            else {
                return current;
            }
        }
        return current;
    }
    
    private static int getFurthestLen(
        BlockPos pos,
        Direction direction,
        Predicate<BlockPos> predicate
    ) {
        for (int i = 1; i < 64; i++) {
            BlockPos newPos = pos.add(direction.getVector().multiply(i));
            if (!predicate.test(newPos)) {
                return i;
            }
        }
        return 64;
    }
    
    
}
