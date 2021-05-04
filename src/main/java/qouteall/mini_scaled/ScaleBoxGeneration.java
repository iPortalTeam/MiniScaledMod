package qouteall.mini_scaled;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;

public class ScaleBoxGeneration {
    public static void putScaleBox(
        ServerWorld world,
        ServerPlayerEntity player,
        int size,
        BlockPos pos,
        DyeColor color
    ) {
        ScaleBoxRecord record = ScaleBoxRecord.get();
        
        ScaleBoxRecord.Entry entry = record.entries.stream().filter(e -> {
            return e.ownerId.equals(player.getUuid()) && e.color == color &&
                e.size == size;
        }).findFirst().orElse(null);
        
        if (entry == null) {
            int newId = allocateId(record);
            ScaleBoxRecord.Entry newEntry = new ScaleBoxRecord.Entry();
            newEntry.id = newId;
            newEntry.color = color;
            newEntry.ownerId = player.getUuid();
            newEntry.size = size;
            record.entries.add(newEntry);
            record.setDirty(true);
            
            entry = newEntry;
        }
        
        
    }
    
    private static int allocateId(ScaleBoxRecord record) {
        return record.entries.stream().mapToInt(e -> e.id).max().orElse(0) + 1;
    }
}
