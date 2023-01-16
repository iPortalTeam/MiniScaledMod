package qouteall.mini_scaled;

import org.apache.commons.lang3.Validate;
import qouteall.mini_scaled.block.BoxBarrierBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlockEntity;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ScaleBoxManipulation {
    private static AARotation getEntranceRotationForPlacing(UseOnContext context) {
        Validate.isTrue(context.getPlayer() != null);
        
        Direction placingSide = context.getClickedFace();
        
        Direction[] otherDirs = Helper.getAnotherFourDirections(placingSide.getAxis());
        
        Vec3 hitPosToEye = context.getPlayer().getEyePosition().subtract(context.getClickLocation());
        
        Direction directionPointingToPlayer = Arrays.stream(otherDirs).max(
            Comparator.comparingDouble(dir -> {
                Vec3 vec = Vec3.atLowerCornerOf(dir.getNormal());
                return vec.dot(hitPosToEye);
            })
        ).orElseThrow();
        
        return AARotation.getAARotationFromYZ(placingSide, directionPointingToPlayer);
    }
    
    static InteractionResult onRightClickUsingEntrance(UseOnContext context) {
        Level world = context.getLevel();
        
        if (world.isClientSide()) {
            return InteractionResult.FAIL;
        }
        
        if (context.getPlayer() == null) {
            return InteractionResult.FAIL;
        }
        
        BlockPos pointedBlockPos = context.getClickedPos();
        BlockPos placementPos = pointedBlockPos.relative(context.getClickedFace());
        
        if (!world.isEmptyBlock(placementPos)) {
            return InteractionResult.FAIL;
        }
        
        if (world.getBlockState(pointedBlockPos).getBlock() == ScaleBoxPlaceholderBlock.instance) {
            return InteractionResult.PASS;
        }
        
        ItemStack stack = context.getItemInHand();
        ScaleBoxEntranceItem.ItemInfo itemInfo = new ScaleBoxEntranceItem.ItemInfo(stack.getOrCreateTag());
        
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        
        int scale = itemInfo.scale;
        if (!ScaleBoxGeneration.isValidScale(scale)) {
            player.displayClientMessage(Component.literal("invalid scale"), false);
            return InteractionResult.FAIL;
        }
        
        UUID ownerId = itemInfo.ownerId;
        String ownerNameCache = itemInfo.ownerNameCache;
        
        if (ownerId == null) {
            ownerId = player.getUUID();
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
        
        BlockPos realPlacementPos = IntBox.getBoxByPosAndSignedSize(BlockPos.ZERO, transformedEntranceSize)
            .stream()
            .map(offsetFromBasePosToPlacementPos -> placementPos.subtract(offsetFromBasePosToPlacementPos))
            .filter(basePosCandidate -> IntBox.getBoxByPosAndSignedSize(basePosCandidate, transformedEntranceSize)
                .stream().allMatch(
                    blockPos -> world.getBlockState(blockPos).isAir()
                        && !blockPos.equals(player.blockPosition()) // should not intersect with player
                )
            )
            .findFirst().orElse(null);
        
        if (realPlacementPos != null) {
            ScaleBoxGeneration.putScaleBoxIntoWorld(
                entry,
                ((ServerLevel) world),
                realPlacementPos,
                entranceRotation
            );
            
            stack.shrink(1);
            
            return InteractionResult.SUCCESS;
        }
        else {
            player.displayClientMessage(
                Component.translatable(
                    "mini_scaled.no_enough_space_to_place_scale_box",
                    String.format("(%d, %d, %d)", entranceSize.getX(), entranceSize.getY(), entranceSize.getZ())
                ),
                false
            );
            return InteractionResult.FAIL;
        }
    }
    
    public static InteractionResult onHandRightClickEntrance(
        Player player,
        Level world,
        InteractionHand hand,
        BlockHitResult hitResult
    ) {
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        
        ItemStack stack = player.getItemInHand(hand);
        
        Item item = stack.getItem();
        
        BlockPos pointedBlockPos = hitResult.getBlockPos();
        BlockEntity be = world.getBlockEntity(pointedBlockPos);
        if (!(be instanceof ScaleBoxPlaceholderBlockEntity placeholderBlockEntity)) {
            player.displayClientMessage(Component.literal("Error no block entity"), false);
            return InteractionResult.FAIL;
        }
        
        int boxId = placeholderBlockEntity.boxId;
        ScaleBoxRecord.Entry entry = ScaleBoxRecord.get().getEntryById(boxId);
        if (entry == null) {
            player.displayClientMessage(Component.literal("Error invalid box id"), false);
            return InteractionResult.FAIL;
        }
        
        Direction outerSide = hitResult.getDirection();
        Direction innerSide = entry.getRotationToInner().transformDirection(outerSide);
        
        if (item == ScaleBoxEntranceItem.instance) {
            // try to expand the scale box
            
            if (innerSide.getAxisDirection() != Direction.AxisDirection.POSITIVE) {
                player.displayClientMessage(
                    Component.translatable("mini_scaled.cannot_expand_direction"),
                    false
                );
                return InteractionResult.FAIL;
            }
            
            ScaleBoxEntranceItem.ItemInfo itemInfo = new ScaleBoxEntranceItem.ItemInfo(stack.getOrCreateTag());
            
            UUID ownerId = itemInfo.ownerId;
            if (ownerId == null) {
                ownerId = player.getUUID();
            }
            
            if (!Objects.equals(entry.ownerId, ownerId) ||
                entry.color != itemInfo.color ||
                entry.scale != itemInfo.scale
            ) {
                player.displayClientMessage(
                    Component.translatable("mini_scaled.cannot_expand_mismatch"),
                    false
                );
                return InteractionResult.FAIL;
            }
            
            return tryToExpandScaleBox(player, (ServerLevel) world, stack, entry, outerSide, innerSide);
        }
        else if (stack.isEmpty()) {
            // try to shrink the scale box
            
            if (innerSide.getAxisDirection() != Direction.AxisDirection.POSITIVE) {
                player.displayClientMessage(
                    Component.translatable("mini_scaled.cannot_shrink_direction"),
                    false
                );
                return InteractionResult.FAIL;
            }
            
            BlockPos oldEntranceSize = entry.currentEntranceSize;
            
            int lenOnDirection = Helper.getCoordinate(oldEntranceSize, innerSide.getAxis());
            
            if (lenOnDirection == 1) {
                return InteractionResult.FAIL;
            }
            
            BlockPos newEntranceSize = Helper.putCoordinate(oldEntranceSize, innerSide.getAxis(), lenOnDirection - 1);
            
            IntBox oldInnerOffsets = IntBox.fromBasePointAndSize(BlockPos.ZERO, oldEntranceSize);
            IntBox newInnerOffsets = IntBox.fromBasePointAndSize(BlockPos.ZERO, newEntranceSize);
            boolean hasRemainingBlocks = oldInnerOffsets.stream().anyMatch(offset -> {
                if (newInnerOffsets.contains(offset)) {return false;}
                IntBox innerUnitBox = entry.getInnerUnitBox(offset);
                
                ServerLevel voidWorld = VoidDimension.getVoidWorld();
                
                return !innerUnitBox.stream().allMatch(blockPos -> {
                    BlockState blockState = voidWorld.getBlockState(blockPos);
                    return blockState.getBlock() == BoxBarrierBlock.instance ||
                        blockState.isAir() ||
                        (player.isCreative() && blockState.getBlock() instanceof StainedGlassBlock);
                    // in creative mode, the glass is considered clear
                });
            });
            if (hasRemainingBlocks) {
                player.displayClientMessage(
                    Component.translatable("mini_scaled.cannot_shrink_has_blocks"),
                    false
                );
                return InteractionResult.FAIL;
            }
            
            entry.currentEntranceSize = newEntranceSize;
            ScaleBoxRecord.get().setDirty(true);
            
            ScaleBoxGeneration.putScaleBoxIntoWorld(
                entry,
                ((ServerLevel) world),
                entry.currentEntrancePos,
                entry.getEntranceRotation()
            );
            
            ScaleBoxGeneration.initializeInnerBoxBlocks(
                oldEntranceSize, entry
            );
            
            if (!player.isCreative()) {
                int compensateNetheriteNum = getVolume(oldEntranceSize) - getVolume(newEntranceSize);
                player.addItem(new ItemStack(
                    ScaleBoxEntranceCreation.creationItem,
                    compensateNetheriteNum
                ));
            }
            
            return InteractionResult.SUCCESS;
        }
        else {
            return InteractionResult.PASS;
        }
    }
    
    private static int getVolume(BlockPos entranceSize) {
        return entranceSize.getX() * entranceSize.getY() * entranceSize.getZ();
    }
    
    private static InteractionResult tryToExpandScaleBox(
        Player player, ServerLevel world, ItemStack stack,
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
        
        boolean areaClear = IntBox.getBoxByPosAndSignedSize(entry.currentEntrancePos, transformedNewEntranceSize)
            .fastStream().allMatch(blockPos -> {
                BlockState blockState = world.getBlockState(blockPos);
                return blockState.isAir() || blockState.getBlock() == ScaleBoxPlaceholderBlock.instance;
            });
        
        if (!areaClear) {
            return InteractionResult.FAIL;
        }
        
        int requiredEntranceItemNum = getVolume(newEntranceSize) - getVolume(oldEntranceSize);
        
        if ((!player.isCreative()) && (stack.getCount() < requiredEntranceItemNum)) {
            player.displayClientMessage(
                Component.translatable("mini_scaled.cannot_expand_not_enough", requiredEntranceItemNum),
                false
            );
            return InteractionResult.FAIL;
        }
        
        if ((lenOnDirection + 1) * entry.scale > 64) {
            player.displayClientMessage(
                Component.translatable("mini_scaled.cannot_expand_size_limit"),
                false
            );
            return InteractionResult.FAIL;
        }
        
        if (!player.isCreative()) {
            stack.shrink(requiredEntranceItemNum);
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
        
        return InteractionResult.SUCCESS;
    }
}
