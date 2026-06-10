package com.stutteranalyzer.optimize;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModsFolderScanner {

    private static final Logger LOGGER = LogManager.getLogger("StutterAnalyzer-ModScan");

    // Returns map of normalized mod ID -> best display name guess for every jar in modsDir
    public static Map<String, String> scan(Path modsDir) {
        Map<String, String> result = new HashMap<>();
        if (modsDir == null || !Files.isDirectory(modsDir)) return result;

        try (Stream<Path> files = Files.list(modsDir)) {
            files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                 .forEach(jar -> {
                     try {
                         String id = extractModId(jar);
                         if (id != null && !id.isBlank()) {
                             result.put(id.toLowerCase(), guessDisplayName(jar.getFileName().toString()));
                         }
                     } catch (Exception e) {
                         LOGGER.debug("[SA] ModScan: could not read {}: {}", jar.getFileName(), e.getMessage());
                     }
                 });
        } catch (Exception e) {
            LOGGER.warn("[SA] ModScan: mods folder scan failed: {}", e.getMessage());
        }
        return result;
    }

    private static String extractModId(Path jar) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            // Fabric: fabric.mod.json
            ZipEntry fabricEntry = zf.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                String content = new String(zf.getInputStream(fabricEntry).readAllBytes(), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(content);
                if (m.find()) return m.group(1);
            }
            // Forge/NeoForge: META-INF/mods.toml
            ZipEntry forgeEntry = zf.getEntry("META-INF/mods.toml");
            if (forgeEntry != null) {
                String content = new String(zf.getInputStream(forgeEntry).readAllBytes(), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("(?m)^\\s*modId\\s*=\\s*\"([^\"]+)\"").matcher(content);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {}
        // Fallback: derive from filename (e.g. sodium-0.5.8+mc1.20.4.jar -> sodium)
        return deriveIdFromFilename(jar.getFileName().toString());
    }

    private static String deriveIdFromFilename(String filename) {
        String base = filename.replaceAll("(?i)\\.jar$", "");
        // Strip version part (everything after first hyphen followed by a digit)
        return base.replaceAll("-[0-9].*", "").toLowerCase();
    }

    private static String guessDisplayName(String filename) {
        String base = filename.replaceAll("(?i)\\.jar$", "").replaceAll("-[0-9].*", "");
        StringBuilder sb = new StringBuilder();
        for (String word : base.split("[-_]")) {
            if (!word.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
