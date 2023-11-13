package qouteall.mini_scaled;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.Validate;
import qouteall.mini_scaled.gui.ScaleBoxInteractionManager;
import qouteall.q_misc_util.my_util.IntBox;

public class MiniScaledCommand {
    
    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx
    ) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("miniscaled");
        
        builder.then(Commands.literal("open_gui")
            .requires(context -> context.hasPermission(2))
            .executes(context -> {
                MinecraftServer server = context.getSource().getServer();
                ServerPlayer player = context.getSource().getPlayer();
                Validate.notNull(player, "player is null");
                
                ScaleBoxInteractionManager.get(server).openManagementGui(player, null);
                
                return 0;
            })
        );
        
        builder.then(Commands.literal("view_box_of_all_players")
            .requires(context -> context.hasPermission(2))
            .executes(context -> {
                MinecraftServer server = context.getSource().getServer();
                ServerPlayer player = context.getSource().getPlayer();
                Validate.notNull(player, "player is null");
                
                ScaleBoxInteractionManager.get(server).openManagementGuiForAllScaleBoxes(
                    player, null
                );
                
                return 0;
            })
        );
        
        builder.then(Commands.literal("make_frame")
            .requires(context -> context.hasPermission(2))
            .then(Commands.argument("block", BlockStateArgument.block(ctx))
                .then(Commands.argument("p1", BlockPosArgument.blockPos())
                    .then(Commands.argument("p2", BlockPosArgument.blockPos())
                        .executes(context -> {
                            BlockInput blockInput = BlockStateArgument.getBlock(context, "block");
                            BlockPos p1 = BlockPosArgument.getLoadedBlockPos(context, "p1");
                            BlockPos p2 = BlockPosArgument.getLoadedBlockPos(context, "p2");
                            
                            ServerLevel world = context.getSource().getLevel();
                            
                            BlockState blockState = blockInput.getState();
                            
                            IntBox box = new IntBox(p1, p2);
                            for (IntBox edge : box.get12Edges()) {
                                edge.fastStream().forEach(p -> {
                                    world.setBlockAndUpdate(p, blockState);
                                });
                            }
                            
                            return 0;
                        })
                    )
                )
            )
        );
        
//        builder.then(Commands.literal("custom_setblock")
//            .requires(context -> context.hasPermission(2))
//            .then(Commands.argument("block", BlockStateArgument.block(ctx))
//                .then(Commands.argument("pos", BlockPosArgument.blockPos())
//                    .executes(context -> {
//                        BlockInput blockInput = BlockStateArgument.getBlock(context, "block");
//                        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
//
//                        ServerLevel world = context.getSource().getLevel();
//
//                        BlockState blockState = blockInput.getState();
//
//                        world.setBlock(pos, blockState, 2);
//
//                        return 0;
//                    })
//                )
//            )
//        );
        
        dispatcher.register(builder);
    }
}
