package com.stutteranalyzer.knowledge;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.List;

public class ModInventory {

    public static final class ModEntry {
        public final String modId;
        public final String displayName;
        public final String version;
        public ModEntry(String modId, String displayName, String version) {
            this.modId = modId;
            this.displayName = displayName;
            this.version = version;
        }
    }

    public static List<ModEntry> snapshot() {
        List<ModEntry> entries = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            entries.add(new ModEntry(
                info.getModId(),
                info.getDisplayName(),
                info.getVersion().toString()
            ));
        }
        return entries;
    }

    public static boolean isInstalled(String modId) {
        return ModList.get().isLoaded(modId);
    }
}

