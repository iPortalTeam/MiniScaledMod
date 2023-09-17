package qouteall.mini_scaled;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.Validate;
import qouteall.mini_scaled.gui.ScaleBoxGuiManager;

public class MiniScaledCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("miniscaled");
        
        builder.then(Commands.literal("open_gui")
            .executes(context -> {
                MinecraftServer server = context.getSource().getServer();
                ServerPlayer player = context.getSource().getPlayer();
                Validate.notNull(player);
                
                ScaleBoxGuiManager.get(server).openGui(player, null);
                
                return 0;
            })
        );
        
        dispatcher.register(builder);
    }
}
