package com.stutteranalyzer.mixin;

import com.stutteranalyzer.client.DebugHudStatusProvider;
import com.stutteranalyzer.client.F3StatusFormatter;
import com.stutteranalyzer.config.SAConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugHudMixin {

    /**
     * Intercept the complete left debug text list right before it is passed to renderLines().
     * At this point drawGameInformation() has already appended "Debug charts" and
     * "For help: press F3 + Q", so SA line appears as the last entry.
     */
    @ModifyArg(
        method = "drawGameInformation",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;renderLines(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;Z)V"
        ),
        index = 1
    )
    private List<String> stutteranalyzer$appendSaLine(List<String> list) {
        if (!SAConfig.INSTANCE.debugHudEnabled.get()) return list;
        if (!DebugHudStatusProvider.isF3Enabled()) return list;

        String line = F3StatusFormatter.format();
        if (line == null || line.isBlank()) return list;

        List<String> out = new ArrayList<>(list);
        out.add(line);
        return out;
    }
}
