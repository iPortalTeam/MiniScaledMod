package qouteall.mini_scaled;

import net.minecraft.block.BlockState;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.mini_scaled.util.MSUtil;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

public class ScaleBoxManipulation {
    private static AARotation getEntranceRotationForPlacing(ItemUsageContext context) {
        Validate.isTrue(context.getPlayer() != null);
        
        Direction placingSide = context.getSide();
        
        Direction[] otherDirs = Helper.getAnotherFourDirections(placingSide.getAxis());
        
        Vec3d hitPosToEye = context.getPlayer().getEyePos().subtract(context.getHitPos());
        
        Direction directionPointingToPlayer = Arrays.stream(otherDirs).max(
            Comparator.comparingDouble(dir -> {
                Vec3d vec = Vec3d.of(dir.getVector());
                return vec.dotProduct(hitPosToEye);
            })
        ).orElseThrow();
        
        return AARotation.getAARotationFromYZ(placingSide, directionPointingToPlayer);
    }
    
    static ActionResult onRightClickUsingEntrance(ItemUsageContext context) {
        World world = context.getWorld();
        
        if (world.isClient()) {
            return ActionResult.FAIL;
        }
        
        if (context.getPlayer() == null) {
            return ActionResult.FAIL;
        }
        
        BlockPos pointedBlockPos = context.getBlockPos();
        BlockPos placementPos = pointedBlockPos.offset(context.getSide());
        
        if (!world.isAir(placementPos)) {
            return ActionResult.FAIL;
        }
        
        if (world.getBlockState(pointedBlockPos).getBlock() == ScaleBoxPlaceholderBlock.instance) {
            return ActionResult.PASS;
        }
        
        ItemStack stack = context.getStack();
        ScaleBoxEntranceItem.ItemInfo itemInfo = new ScaleBoxEntranceItem.ItemInfo(stack.getOrCreateNbt());
        
        ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
        
        int scale = itemInfo.scale;
        if (!ScaleBoxGeneration.isValidScale(scale)) {
            player.sendMessage(Text.literal("invalid scale"), false);
            return ActionResult.FAIL;
        }
        
        UUID ownerId = itemInfo.ownerId;
        String ownerNameCache = itemInfo.ownerNameCache;
        
        if (ownerId == null) {
            ownerId = player.getUuid();
        }
        if (ownerNameCache == null) {
            ownerNameCache = player.getName().getString();
        }
        
        DyeColor color = itemInfo.color;
        
        ScaleBoxRecord record = ScaleBoxRecord.get();
        
        ScaleBoxRecord.Entry entry = ScaleBoxGeneration.getOrCreateEntry(
            ownerId, ownerNameCache, scale, color, record
        );
        
        AARotation entranceRotation = getEntranceRotationForPlacing(context);
        
        BlockPos entranceSize = entry.currentEntranceSize;
        BlockPos transformedEntranceSize = entranceRotation.transform(entranceSize);
        
        BlockPos realPlacementPos = MSUtil.getBoxByPosAndSignedSize(BlockPos.ORIGIN, transformedEntranceSize)
            .stream()
            .map(offsetFromBasePosToPlacementPos -> placementPos.subtract(offsetFromBasePosToPlacementPos))
            .filter(basePosCandidate -> MSUtil.getBoxByPosAndSignedSize(basePosCandidate, transformedEntranceSize)
                .stream().allMatch(
                    blockPos -> world.getBlockState(blockPos).isAir()
                        && !blockPos.equals(player.getBlockPos()) // should not intersect with player
                )
            )
            .findFirst().orElse(null);
        
        if (realPlacementPos != null) {
            ScaleBoxGeneration.putScaleBoxIntoWorld(
                entry,
                ((ServerWorld) world),
                realPlacementPos,
                entranceRotation
            );
            
            stack.decrement(1);
    
            return ActionResult.SUCCESS;
        }
        else {
            player.sendMessage(
                Text.translatable(
                    "mini_scaled.no_enough_space_to_place_scale_box",
                    String.format("(%d, %d, %d)", entranceSize.getX(), entranceSize.getY(), entranceSize.getZ())
                ),
                false
            );
            return ActionResult.FAIL;
        }
    }
    
    public static ActionResult onHandRightClickEntrance(
        PlayerEntity player,
        World world,
        Hand hand,
        BlockHitResult hitResult
    ) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        
        ItemStack stack = player.getStackInHand(hand);
        
        Item item = stack.getItem();
        
        BlockPos pointedBlockPos = hitResult.getBlockPos();
        BlockEntity be = world.getBlockEntity(pointedBlockPos);
        if (!(be instanceof ScaleBoxPlaceholderBlockEntity placeholderBlockEntity)) {
            player.sendMessage(Text.literal("Error no block entity"), false);
            return ActionResult.FAIL;
        }
        
        int boxId = placeholderBlockEntity.boxId;
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.get().getEntryById(boxId);
        if (entry == null) {
            player.sendMessage(Text.literal("Error invalid box id"), false);
            return ActionResult.FAIL;
        }
        
        Direction outerSide = hitResult.getSide();
        Direction innerSide = entry.getRotationToInner().transformDirection(outerSide);
        
        if (item == ScaleBoxEntranceItem.instance) {
            // try to expand the scale box
            
            if (innerSide.getDirection() != Direction.AxisDirection.POSITIVE) {
                player.sendMessage(
                    Text.translatable("mini_scaled.cannot_expand_direction"),
                    false
                );
                return ActionResult.FAIL;
            }
            
            ScaleBoxEntranceItem.ItemInfo itemInfo = new ScaleBoxEntranceItem.ItemInfo(stack.getOrCreateNbt());
            
            UUID ownerId = itemInfo.ownerId;
            if (ownerId == null) {
                ownerId = player.getUuid();
            }
            
            if (!Objects.equals(entry.ownerId, ownerId) ||
                entry.color != itemInfo.color ||
                entry.scale != itemInfo.scale
            ) {
                player.sendMessage(
                    Text.translatable("mini_scaled.cannot_expand_mismatch"),
                    false
                );
                return ActionResult.FAIL;
            }
            
            return tryToExpandScaleBox(player, (ServerWorld) world, stack, entry, outerSide, innerSide);
        }
        else if (stack.isEmpty()) {
            // try to shrink the scale box
            
            if (innerSide.getDirection() != Direction.AxisDirection.POSITIVE) {
                player.sendMessage(
                    Text.translatable("mini_scaled.cannot_shrink_direction"),
                    false
                );
                return ActionResult.FAIL;
            }
            
            BlockPos oldEntranceSize = entry.currentEntranceSize;
            
            int lenOnDirection = Helper.getCoordinate(oldEntranceSize, innerSide.getAxis());
            
            if (lenOnDirection == 1) {
                return ActionResult.FAIL;
            }
            
            BlockPos newEntranceSize = Helper.putCoordinate(oldEntranceSize, innerSide.getAxis(), lenOnDirection - 1);
            
            IntBox oldInnerOffsets = IntBox.fromBasePointAndSize(BlockPos.ORIGIN, oldEntranceSize);
            IntBox newInnerOffsets = IntBox.fromBasePointAndSize(BlockPos.ORIGIN, newEntranceSize);
            boolean hasRemainingBlocks = oldInnerOffsets.stream().anyMatch(offset -> {
                if (newInnerOffsets.contains(offset)) {return false;}
                IntBox innerUnitBox = entry.getInnerUnitBox(offset);
                
                ServerWorld voidWorld = VoidDimension.getVoidWorld();
                
                return !innerUnitBox.stream().allMatch(blockPos -> {
                    BlockState blockState = voidWorld.getBlockState(blockPos);
                    return blockState.getBlock() == BoxBarrierBlock.instance ||
                        blockState.isAir() ||
                        (player.isCreative() && blockState.getBlock() instanceof StainedGlassBlock);
                    // in creative mode, the glass is considered clear
                });
            });
            if (hasRemainingBlocks) {
                player.sendMessage(
                    Text.translatable("mini_scaled.cannot_shrink_has_blocks"),
                    false
                );
                return ActionResult.FAIL;
            }
            
            entry.currentEntranceSize = newEntranceSize;
            ScaleBoxRecord.get().setDirty(true);
            
            ScaleBoxGeneration.putScaleBoxIntoWorld(
                entry,
                ((ServerWorld) world),
                entry.currentEntrancePos,
                entry.getEntranceRotation()
            );
            
            ScaleBoxGeneration.initializeInnerBoxBlocks(
                oldEntranceSize, entry
            );
            
            if (!player.isCreative()) {
                int compensateNetheriteNum = getVolume(oldEntranceSize) - getVolume(newEntranceSize);
                player.giveItemStack(new ItemStack(
                    ScaleBoxEntranceCreation.creationItem,
                    compensateNetheriteNum
                ));
            }
            
            return ActionResult.SUCCESS;
        }
        else {
            return ActionResult.PASS;
        }
    }
    
    private static int getVolume(BlockPos entranceSize) {
        return entranceSize.getX() * entranceSize.getY() * entranceSize.getZ();
    }
    
    private static ActionResult tryToExpandScaleBox(
        PlayerEntity player, ServerWorld world, ItemStack stack,
        ScaleBoxRecord.Entry entry, Direction outerSide, Direction innerSide
    ) {
        // expand existing scale box
        
        BlockPos oldEntranceSize = entry.currentEntranceSize;
        int volume = getVolume(oldEntranceSize);
        
        int lenOnDirection = Helper.getCoordinate(oldEntranceSize, innerSide.getAxis());
        
        BlockPos newEntranceSize = Helper.putCoordinate(
            oldEntranceSize, innerSide.getAxis(), lenOnDirection + 1
        );
        
        BlockPos transformedNewEntranceSize = entry.getEntranceRotation().transform(newEntranceSize);
        
        boolean areaClear = MSUtil.getBoxByPosAndSignedSize(entry.currentEntrancePos, transformedNewEntranceSize)
            .fastStream().allMatch(blockPos -> {
                BlockState blockState = world.getBlockState(blockPos);
                return blockState.isAir() || blockState.getBlock() == ScaleBoxPlaceholderBlock.instance;
            });
        
        if (!areaClear) {
            return ActionResult.FAIL;
        }
        
        int requiredEntranceItemNum = getVolume(newEntranceSize) - getVolume(oldEntranceSize);
        
        if ((!player.isCreative()) && (stack.getCount() < requiredEntranceItemNum)) {
            player.sendMessage(
                Text.translatable("mini_scaled.cannot_expand_not_enough", requiredEntranceItemNum),
                false
            );
            return ActionResult.FAIL;
        }
        
        if ((lenOnDirection + 1) * entry.scale > 64) {
            player.sendMessage(
                Text.translatable("mini_scaled.cannot_expand_size_limit"),
                false
            );
            return ActionResult.FAIL;
        }
        
        if (!player.isCreative()) {
            stack.decrement(requiredEntranceItemNum);
        }
        
        entry.currentEntranceSize = newEntranceSize;
        ScaleBoxRecord.get().setDirty(true);
        
        ScaleBoxGeneration.putScaleBoxIntoWorld(
            entry,
            world,
            entry.currentEntrancePos,
            entry.getEntranceRotation()
        );
        
        ScaleBoxGeneration.initializeInnerBoxBlocks(
            oldEntranceSize, entry
        );
        
        return ActionResult.SUCCESS;
    }
}
