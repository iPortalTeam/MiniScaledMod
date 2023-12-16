package qouteall.mini_scaled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import qouteall.mini_scaled.block.ScaleBoxPlaceholderBlock;
import qouteall.mini_scaled.item.ScaleBoxEntranceItem;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ScaleBoxManipulation {
    
    public static final int MAX_INNER_LEN = 64;
    
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
    
    public static InteractionResult onRightClickUsingEntrance(UseOnContext context) {
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
        
        if (world.getBlockState(pointedBlockPos).getBlock() == ScaleBoxPlaceholderBlock.INSTANCE) {
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
        
        ScaleBoxRecord record = ScaleBoxRecord.get(Objects.requireNonNull(world.getServer()));
        
        ScaleBoxRecord.Entry entry;
        
        if (itemInfo.boxId == null) {
            // for old items, find by owner and color and scale
            
            List<ScaleBoxRecord.Entry> entriesByOwner = record.getEntriesByOwner(ownerId);
            entry = entriesByOwner.stream()
                .filter(e -> e.color == color && e.scale == scale)
                .findFirst().orElse(null);
            
            if (entry == null) {
                player.sendSystemMessage(Component.translatable(
                    "mini_scaled.cannot_find_scale_box_for_item"
                ));
                return InteractionResult.FAIL;
            }
        }
        else {
            // for new items, simply find by box id
            
            entry = record.getEntryById(itemInfo.boxId);
            
            if (entry == null) {
                player.sendSystemMessage(Component.translatable(
                    "mini_scaled.box_not_exist"
                ));
                return InteractionResult.FAIL;
            }
        }
        
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
            if (stack.hasCustomHoverName()) {
                entry.customName = stack.getHoverName().getString();
            }
            
            ScaleBoxOperations.putScaleBoxEntranceIntoWorld(
                entry,
                ((ServerLevel) world),
                realPlacementPos,
                entranceRotation,
                ((ServerPlayer) player),
                null
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
    
    private static int getVolume(BlockPos entranceSize) {
        return entranceSize.getX() * entranceSize.getY() * entranceSize.getZ();
    }
    
    public static void showScaleBoxAccessDeniedMessage(Player player) {
        player.displayClientMessage(
            Component.translatable("mini_scaled.access_denied"), false
        );
    }
}
