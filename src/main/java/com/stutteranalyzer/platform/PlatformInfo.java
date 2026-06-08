package com.stutteranalyzer.platform;

import com.stutteranalyzer.SAEnvironment;

public final class PlatformInfo {

    private PlatformInfo() {}

    public static String loaderName() { return SAEnvironment.getLoaderName(); }

    public static String loaderVersion() { return SAEnvironment.getLoaderVersion(); }

    public static String minecraftVersion() { return "1.20.4"; }

    public static boolean isForge() { return "forge".equalsIgnoreCase(SAEnvironment.getLoaderName()); }

    public static boolean isFabric() { return "fabric".equalsIgnoreCase(SAEnvironment.getLoaderName()); }
}
