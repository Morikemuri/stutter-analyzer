package com.stutteranalyzer.platform;

import com.stutteranalyzer.SAEnvironment;

public final class PlatformInfo {

    private PlatformInfo() {}

    public static String loaderName() { return SAEnvironment.getLoaderName(); }

    public static String loaderVersion() { return SAEnvironment.getLoaderVersion(); }

    // The one true Minecraft version - everything else asks here instead of hardcoding
    public static String minecraftVersion() { return "1.21.4"; }

    public static boolean isForge() { return "forge".equalsIgnoreCase(SAEnvironment.getLoaderName()); }

    public static boolean isFabric() { return "fabric".equalsIgnoreCase(SAEnvironment.getLoaderName()); }
}
