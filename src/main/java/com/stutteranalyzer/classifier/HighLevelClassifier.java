package com.stutteranalyzer.classifier;

import com.stutteranalyzer.SAEnvironment;
import com.stutteranalyzer.events.RecentEventBuffer;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class HighLevelClassifier {

    public record HighLevelResult(
        HighLevelCategory category,
        String userFacingName,
        String classificationReason,
        String rootCauseCertainty
    ) {
        public static HighLevelResult none() {
            return new HighLevelResult(HighLevelCategory.NONE, "", "", "");
        }
    }

    private static final String[] WORLD_JOIN_LOG_PATTERNS = {
        "starting integrated minecraft server",
        "preparing start region",
        "preparing spawn area",
        "changing view distance",
        "changing simulation distance",
        "connected to a modded server",
        "joined the game",
        "loaded advancements",
        "loaded recipes",
        "injecting existing registry data"
    };

    public static HighLevelResult classify(
            FreezeCategory technical,
            List<RecentEventBuffer.GameEvent> recentEvents) {

        if (technical != FreezeCategory.SERVER_TICK_SPIKE
                && technical != FreezeCategory.CLIENT_RENDER_STUTTER) {
            return HighLevelResult.none();
        }

        int total = countEventMatches(recentEvents) + countLogMatches();
        if (total < 2) return HighLevelResult.none();

        if (technical == FreezeCategory.SERVER_TICK_SPIKE) {
            return new HighLevelResult(
                HighLevelCategory.WORLD_JOIN_LOAD_SPIKE,
                "World join / world loading spike",
                "Server tick spike occurred during integrated server startup, spawn preparation, and view/simulation distance changes.",
                "medium"
            );
        } else {
            return new HighLevelResult(
                HighLevelCategory.WORLD_JOIN_RENDER_STALL,
                "World join / render stall",
                "Client render stutter occurred during world join/loading. Render thread catching up after world load.",
                "medium"
            );
        }
    }

    private static int countEventMatches(List<RecentEventBuffer.GameEvent> events) {
        boolean hasIntegratedStart = false;
        boolean hasPlayerJoin = false;
        boolean hasWorldLoad = false;
        for (RecentEventBuffer.GameEvent evt : events) {
            switch (evt.type) {
                case INTEGRATED_SERVER_START -> hasIntegratedStart = true;
                case PLAYER_JOIN -> hasPlayerJoin = true;
                case WORLD_LOAD -> hasWorldLoad = true;
                default -> {}
            }
        }
        return (hasIntegratedStart ? 1 : 0) + (hasPlayerJoin ? 1 : 0) + (hasWorldLoad ? 1 : 0);
    }

    private static int countLogMatches() {
        try {
            Path logFile = SAEnvironment.getLogFile();
            if (logFile == null || !Files.exists(logFile)) return 0;
            long fileSize = Files.size(logFile);
            long readOffset = Math.max(0, fileSize - 65536L);
            byte[] buf;
            try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                raf.seek(readOffset);
                int len = (int) (fileSize - readOffset);
                buf = new byte[len];
                raf.readFully(buf);
            }
            String tail = new String(buf, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            int count = 0;
            for (String pattern : WORLD_JOIN_LOG_PATTERNS) {
                if (tail.contains(pattern)) count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }
}
