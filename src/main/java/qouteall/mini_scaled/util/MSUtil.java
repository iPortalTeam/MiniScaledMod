package qouteall.mini_scaled.util;

import net.minecraft.core.BlockPos;
import qouteall.q_misc_util.my_util.IntBox;

public class MSUtil {
    // TODO move to ImmPtl
    public static IntBox getBoxByPosAndSignedSize(
        BlockPos basePos,
        BlockPos signedSize
    ) {
        return new IntBox(
            basePos,
            new BlockPos(
                getEndCoord(basePos.getX(), signedSize.getX()),
                getEndCoord(basePos.getY(), signedSize.getY()),
                getEndCoord(basePos.getZ(), signedSize.getZ())
            )
        );
    }
    
    private static int getEndCoord(int base, int signedSize) {
        if (signedSize > 0) {
            return base + signedSize - 1;
        }
        else if (signedSize < 0) {
            return base + signedSize + 1;
        }
        else {
            throw new IllegalArgumentException("Signed size cannot be zero");
        }
    }
    
}
