package com.stutteranalyzer.mixin;

import com.stutteranalyzer.client.DebugHudStatusProvider;
import com.stutteranalyzer.client.F3StatusFormatter;
import com.stutteranalyzer.config.SAConfig;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugHudMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void stutteranalyzer$appendDebugLine(CallbackInfoReturnable<List<String>> cir) {
        if (!SAConfig.INSTANCE.debugHudEnabled.get()) return;
        if (!DebugHudStatusProvider.isF3Enabled()) return;

        String line = F3StatusFormatter.format();
        if (line == null || line.isBlank()) return;

        List<String> lines = cir.getReturnValue();
        if (lines == null) return;

        lines.add(line);
    }
}
