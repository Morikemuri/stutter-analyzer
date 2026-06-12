package com.stutteranalyzer;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SAEnvironment {

    private static volatile boolean clientSide = false;
    private static volatile Path gameDir = Paths.get(".");
    private static volatile String loaderName = "fabric";
    private static volatile String loaderVersion = "unknown";

    public static void init(boolean isClient, Path dir, String loader, String loaderVer) {
        clientSide = isClient;
        gameDir = dir;
        loaderName = loader;
        loaderVersion = loaderVer;
    }

    public static void setClientSide(boolean value) { clientSide = value; }

    public static boolean isClientSide() { return clientSide; }

    public static Path getGameDir() { return gameDir; }

    public static Path getConfigDir() { return gameDir.resolve("config"); }

    public static Path getLogFile() { return gameDir.resolve("logs/latest.log"); }

    public static String getLoaderName() { return loaderName; }

    public static String getLoaderVersion() { return loaderVersion; }
}
