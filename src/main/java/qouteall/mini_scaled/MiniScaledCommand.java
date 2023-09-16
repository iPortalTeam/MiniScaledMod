package qouteall.mini_scaled;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import qouteall.mini_scaled.gui.ScaleBoxGuiManager;

public class MiniScaledCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("miniscaled");
        
        builder.then(Commands.literal("open_gui")
            .then(Commands.argument("boxId", IntegerArgumentType.integer())
                .executes(context -> {
                    int boxId = IntegerArgumentType.getInteger(context, "boxId");
                    
                    MinecraftServer server = context.getSource().getServer();
                    ServerPlayer player = context.getSource().getPlayer();
                    
                    ScaleBoxGuiManager.get(server).onUpdateGui(player, boxId);
                    
                    return 0;
                })
            )
        );
        
        dispatcher.register(builder);
    }
}
