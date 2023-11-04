package qouteall.mini_scaled;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.q_misc_util.my_util.IntBox;

public class ClientUnwrappingInteraction {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static @Nullable Session session = null;
    
    public static record Session(
        ResourceKey<Level> dimension,
        int boxId,
        IntBox entranceArea,
        int scale
    ) {
    
    }
    
    public static void init() {
        IPGlobal.clientCleanupSignal.connect(ClientUnwrappingInteraction::reset);
    }
    
    private static void reset() {
        session = null;
    }
    
    public static void startPreUnwrapping(
        ScaleBoxRecord.Entry entry
    ) {
        if (entry.currentEntranceDim == null || entry.currentEntrancePos == null) {
            LOGGER.warn("Invalid box info {}", entry.toTag());
            reset();
            return;
        }
        
        session = new Session(
            entry.currentEntranceDim, entry.id, entry.getOuterAreaBox(), entry.scale
        );
    }
}
