package qouteall.mini_scaled.gui;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.List;

public record PendingScaleBoxWrapping(
    ResourceKey<Level> dimension,
    IntBox glassFrame,
    DyeColor color,
    List<ScaleBoxWrappingScreen.Option> options
) {}
