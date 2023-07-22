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
    
    private static final List<BoxFrameMatcher> boxFrameMatchers =
        Arrays.stream(ScaleBoxGeneration.supportedScales).mapToObj(
            s -> new BoxFrameMatcher(new BlockPos(s, s, s))
        ).collect(Collectors.toList());
    
    
    public static void init() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (creationItem == null) {
                return InteractionResult.PASS;
            }
            
            ItemStack stackInHand = player.getItemInHand(hand);
            
            if (player instanceof ServerPlayer serverPlayer) {
                if (stackInHand.getItem() == creationItem) {
                    boolean succeeded =
                        onRightClickBoxFrameUsingNetherite(serverPlayer, hitResult.getBlockPos());
                    if (succeeded) {
                        stackInHand.shrink(1);
                        return InteractionResult.CONSUME;
                    }
                    else {
                        return InteractionResult.FAIL;
                    }
                }
            }
            
            return InteractionResult.PASS;
        });
    }
    
    private static boolean onRightClickBoxFrameUsingNetherite(
        ServerPlayer player,
        BlockPos pos
    ) {
        ServerLevel world = (ServerLevel) player.level();
        
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
                player.displayClientMessage(Component.translatable(
                    "mini_scaled.invalid_box_size",
                    String.format("(%d,%d,%d)",
                        size.getX(), size.getY(), size.getZ()
                    )
                ), false);
            }
            else {
                player.displayClientMessage(Component.translatable(
                    "mini_scaled.glass_frame_incomplete",
                    String.format("(%d,%d,%d)",
                        size.getX(), size.getY(), size.getZ()
                    )
                ), false);
            }
            
            return false;
        }
        
        int boxLen = box.getSize().getX();
        
        // give player the entrance
        DyeColor color = stainedGlassBlock.getColor();
        ItemStack itemStack = new ItemStack(ScaleBoxEntranceItem.instance);
        ScaleBoxEntranceItem.ItemInfo itemInfo = new ScaleBoxEntranceItem.ItemInfo(boxLen, color);
        itemInfo.writeToTag(itemStack.getOrCreateTag());
        player.addItem(itemStack);
        
        // give player a wand
        player.addItem(new ItemStack(ManipulationWandItem.instance));
        
        // remove the frame
        for (IntBox edge : box.get12Edges()) {
            edge.fastStream().forEach(p -> {
                world.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            });
        }
        
        return true;
    }
    
    public static class BoxFrameMatcher {
        private final BlockPos size;
        private final BlockPos[] vertexOffsets;
        
        public BoxFrameMatcher(BlockPos size) {
            this.size = size;
    
            vertexOffsets = IntBox.fromBasePointAndSize(BlockPos.ZERO, size).getEightVertices();
        }
        
        public IntBox matchFromVertex(BlockPos pos, Predicate<BlockPos> blockPredicate) {
            for (BlockPos vertexOffset : vertexOffsets) {
                BlockPos basePos = pos.subtract(vertexOffset);
                
                if (blockPredicate.test(basePos)) {
                    IntBox box = IntBox.fromBasePointAndSize(basePos, size);
                    
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
        ServerLevel world,
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
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
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
        BlockPos.MutableBlockPos currentPos,
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
    
    private static int getFurthestLen(
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
    
    private static IntBox detectBoxSizeForErrorFeedback(
        ServerLevel world,
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
    
        return IntBox.fromBasePointAndSize(currPos, size);
    }
    
    
}
