package io.github.morikemuri.stutteranalyzer.platform;

import io.github.morikemuri.stutteranalyzer.SAEnvironment;

import java.nio.file.Path;

public class SAFabricPlatform {

    public static Path getGameDir() {
        return SAEnvironment.getGameDir();
    }

    public static Path getConfigDir() {
        return SAEnvironment.getConfigDir();
    }

    public static boolean isClient() {
        return SAEnvironment.isClientSide();
    }

    public static Path getLogFile() {
        return SAEnvironment.getLogFile();
    }
}
