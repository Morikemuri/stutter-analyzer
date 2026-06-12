package com.stutteranalyzer.knowledge;

import net.fabricmc.loader.api.FabricLoader;

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
        for (var container : FabricLoader.getInstance().getAllMods()) {
            var meta = container.getMetadata();
            entries.add(new ModEntry(
                meta.getId(),
                meta.getName(),
                meta.getVersion().getFriendlyString()
            ));
        }
        return entries;
    }

    public static boolean isInstalled(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
