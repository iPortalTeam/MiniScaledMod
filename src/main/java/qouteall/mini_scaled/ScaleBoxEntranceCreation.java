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
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ScaleBoxEntranceCreation {
    
    private static final List<BoxFrameMatcher> boxFrameMatchers =
        Arrays.stream(ScaleBoxGeneration.supportedScales).mapToObj(
            s -> new BoxFrameMatcher(new BlockPos(s, s, s))
        ).collect(Collectors.toList());
    
    
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
        
        IntBox box = detectBoxFrameOfAllowedSize(world, pos, originBlockState);
        
        if (box == null) {
            IntBox roughBox = detectBoxSizeForErrorFeedback(world, pos, originBlockState);
            
            BlockPos size = roughBox.getSize();
            
            Predicate<BlockPos> blockPredicate = p -> world.getBlockState(p) == originBlockState;
            boolean roughBoxFrameComplete =
                Arrays.stream(roughBox.get12Edges()).allMatch(edge -> edge.fastStream().allMatch(blockPredicate));
    
            if (roughBoxFrameComplete) {
                player.sendMessage(new TranslatableText(
                    "mini_scaled.invalid_box_size",
                    String.format("(%d,%d,%d)",
                        size.getX(), size.getY(), size.getZ()
                    )
                ), false);
            }
            else {
                player.sendMessage(new TranslatableText(
                    "mini_scaled.glass_frame_incomplete",
                    String.format("(%d,%d,%d)",
                        size.getX(), size.getY(), size.getZ()
                    )
                ), false);
            }
    
            return false;
        }
        
        int boxLen = box.getSize().getX();
        
        // give player the item
        DyeColor color = stainedGlassBlock.getColor();
        ItemStack itemStack = new ItemStack(ScaleBoxEntranceItem.instance);
        ScaleBoxEntranceItem.ItemInfo itemInfo = new ScaleBoxEntranceItem.ItemInfo(boxLen, color);
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
    
    public static class BoxFrameMatcher {
        private final BlockPos size;
        private final BlockPos[] vertexOffsets;
        
        public BoxFrameMatcher(BlockPos size) {
            this.size = size;
            
            vertexOffsets = IntBox.getBoxByBasePointAndSize(size, BlockPos.ORIGIN).getEightVertices();
        }
        
        public IntBox matchFromVertex(BlockPos pos, Predicate<BlockPos> blockPredicate) {
            for (BlockPos vertexOffset : vertexOffsets) {
                BlockPos basePos = pos.subtract(vertexOffset);
                
                if (blockPredicate.test(basePos)) {
                    IntBox box = IntBox.getBoxByBasePointAndSize(size, basePos);
                    
                    boolean allGlasses = Arrays.stream(box.get12Edges())
                        .allMatch(edge -> edge.fastStream().allMatch(blockPredicate));
                    
                    if (allGlasses) {
                        return box;
                    }
                }
            }
            return null;
        }
    }
    
    @Nullable
    private static IntBox detectBoxFrameOfAllowedSize(
        ServerWorld world,
        BlockPos pos,
        BlockState blockState
    ) {
        Predicate<BlockPos> blockPredicate = p -> world.getBlockState(p) == blockState;
        
        Function<BlockPos, IntBox> matcher = vertexPos -> {
            for (BoxFrameMatcher boxFrameMatcher : boxFrameMatchers) {
                IntBox result = boxFrameMatcher.matchFromVertex(vertexPos, blockPredicate);
                if (result != null) {
                    return result;
                }
            }
            
            return null;
        };
        
        IntBox result = null;
        BlockPos.Mutable mutableBlockPos = new BlockPos.Mutable();
        mutableBlockPos.set(pos);
        
        result = matchAlongPath(mutableBlockPos, Direction.DOWN, blockPredicate, matcher);
        if (result != null) {
            return result;
        }
        
        result = matchAlongPath(mutableBlockPos, Direction.NORTH, blockPredicate, matcher);
        if (result != null) {
            return result;
        }
        
        result = matchAlongPath(mutableBlockPos, Direction.WEST, blockPredicate, matcher);
        if (result != null) {
            return result;
        }
        
        return result;
    }
    
    @Nullable
    private static IntBox matchAlongPath(
        BlockPos.Mutable currentPos,
        Direction direction,
        Predicate<BlockPos> pathPredicate,
        Function<BlockPos, IntBox> matchingFunc
    ) {
        for (int i = 0; i < 64; i++) {
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
    
    private static IntBox detectBoxSizeForErrorFeedback(
        ServerWorld world,
        BlockPos pos,
        BlockState blockState
    ) {
        Predicate<BlockPos> blockPredicate = p -> world.getBlockState(p) == blockState;
        
        BlockPos currPos = pos;
        currPos = getFurthest(currPos, Direction.DOWN, blockPredicate);
        currPos = getFurthest(currPos, Direction.NORTH, blockPredicate);
        currPos = getFurthest(currPos, Direction.WEST, blockPredicate);
        currPos = getFurthest(currPos, Direction.DOWN, blockPredicate);
        currPos = getFurthest(currPos, Direction.NORTH, blockPredicate);
        currPos = getFurthest(currPos, Direction.WEST, blockPredicate);
        
        BlockPos size = new BlockPos(
            getFurthestLen(currPos, Direction.EAST, blockPredicate),
            getFurthestLen(currPos, Direction.UP, blockPredicate),
            getFurthestLen(currPos, Direction.SOUTH, blockPredicate)
        );
        
        return IntBox.getBoxByBasePointAndSize(size, currPos);
    }
    
    
}
