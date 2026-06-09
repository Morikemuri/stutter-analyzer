package com.stutteranalyzer.optimize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stutteranalyzer.command.CommandFeedback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class OptimizeInstaller {

    private static final Logger LOGGER = LogManager.getLogger("StutterAnalyzer-Install");
    private static final long CONFIRM_WINDOW_MS = 60_000L;
    private static final long PLAN_MAX_AGE_MS   = 5L * 60_000L;
    private static final long MAX_FILE_SIZE      = 50L * 1024L * 1024L;

    private static volatile OptimizePlan currentPlan;
    private static volatile Path currentModsDir;
    private static volatile long confirmRequestedAt = 0;

    public static void setPlan(OptimizePlan plan, Path modsDir) {
        currentPlan = plan;
        currentModsDir = modsDir;
        confirmRequestedAt = 0;
        LOGGER.info("[SA] Optimization install target: {}", modsDir.toAbsolutePath());
    }

    public static void handleInstall(CommandSourceStack src) {
        OptimizePlan plan = currentPlan;
        Path modsDir = currentModsDir;

        if (plan == null) {
            src.sendSuccess(() -> CommandFeedback.info(
                "[SA] No optimization plan found. Run /sa optimize suggest first."), false);
            return;
        }
        if (plan.isExpired(PLAN_MAX_AGE_MS)) {
            currentPlan = null;
            confirmRequestedAt = 0;
            src.sendSuccess(() -> CommandFeedback.info(
                "[SA] Plan expired. Run /sa optimize suggest again."), false);
            return;
        }
        if (plan.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.info(
                "[SA] Nothing to install. Your modpack already looks optimized."), false);
            return;
        }
        if (modsDir == null) {
            src.sendSuccess(() -> CommandFeedback.info(
                "[SA] Could not detect mods folder. Install cancelled."), false);
            return;
        }

        long now = System.currentTimeMillis();
        long warned = confirmRequestedAt;

        if (warned == 0 || (now - warned) > CONFIRM_WINDOW_MS) {
            confirmRequestedAt = now;
            showInstallWarning(src, plan, modsDir);
        } else {
            confirmRequestedAt = 0;
            executeInstall(src, plan, modsDir);
        }
    }

    private static void showInstallWarning(CommandSourceStack src, OptimizePlan plan, Path modsDir) {
        src.sendFailure(Component.literal(
            "[SA] WARNING: Installing mods can break modpacks, especially large or heavily customized ones."));
        if (plan.risk == OptimizePlan.RiskLevel.HIGH) {
            src.sendFailure(Component.literal("[SA] Risk is HIGH: " + plan.riskReason));
        }
        src.sendSuccess(() -> CommandFeedback.info("[SA] Planned installs:"), false);
        for (OptimizeMod mod : plan.recommended) {
            boolean hasUrl = mod.resolvedUrl != null && !mod.resolvedUrl.isEmpty();
            String suffix = hasUrl ? "" : " (no download URL - will be skipped)";
            src.sendSuccess(() -> CommandFeedback.info("  - " + mod.displayName + suffix), false);
        }
        src.sendSuccess(() -> CommandFeedback.info(
            "[SA] Files will be downloaded into: " + modsDir.toAbsolutePath()), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Backup your modpack first."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] No guarantee of performance improvement."), false);
        src.sendSuccess(() -> CommandFeedback.info("[SA] Restart required after installation."), false);
        src.sendSuccess(() -> CommandFeedback.info(
            "[SA] Run /sa optimize install again within 60 seconds to confirm."), false);
    }

    private static void executeInstall(CommandSourceStack src, OptimizePlan plan, Path modsDir) {
        src.sendSuccess(() -> CommandFeedback.info(
            "[SA] Downloading compatible optimization mods..."), false);
        Thread worker = new Thread(() -> doInstall(src, plan, modsDir), "SA-OptimizeInstall");
        worker.setDaemon(true);
        worker.start();
    }

    private static void doInstall(CommandSourceStack src, OptimizePlan plan, Path modsDir) {
        List<ManifestEntry> installedList = new ArrayList<>();
        List<ManifestEntry> failedList = new ArrayList<>();
        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (OptimizeMod mod : plan.recommended) {
            if (mod.resolvedUrl == null || mod.resolvedUrl.isEmpty()) {
                String reason = "no compatible file found for " + plan.loader + " " + plan.mcVersion;
                send(src, "[SA] Failed: " + mod.displayName + " - " + reason + ".");
                failedList.add(new ManifestEntry(mod.id, mod.displayName, null, null, null, "failed", reason));
                failCount++;
                continue;
            }

            try {
                DownloadResult result = downloadAndVerify(mod, modsDir);
                if (result.skipped) {
                    send(src, "[SA] Already installed: " + mod.displayName + " -> " + result.filename + " (skipped)");
                    installedList.add(new ManifestEntry(mod.id, mod.displayName,
                        result.filename, modsDir.resolve(result.filename).toString(),
                        mod.resolvedSha512, "skipped", null));
                    skipCount++;
                } else {
                    send(src, "[SA] Installed: " + mod.displayName + " -> " + result.filename);
                    installedList.add(new ManifestEntry(mod.id, mod.displayName,
                        result.filename, modsDir.resolve(result.filename).toString(),
                        mod.resolvedSha512, "installed", null));
                    successCount++;
                }
            } catch (Exception e) {
                LOGGER.warn("[SA] Install failed for {}: {}", mod.displayName, e.getMessage(), e);
                send(src, "[SA] Failed: " + mod.displayName + " - " + e.getMessage());
                failedList.add(new ManifestEntry(mod.id, mod.displayName,
                    null, null, null, "failed", e.getMessage()));
                failCount++;
            }
        }

        writeManifest(plan, installedList, failedList, modsDir.getParent());

        if (successCount == 0 && skipCount == 0 && failCount > 0) {
            send(src, "[SA] No files were installed. Check latest.log for details.");
        } else if (failCount > 0 && successCount > 0) {
            send(src, "[SA] Done with " + failCount + " failure(s). Check latest.log.");
        } else if (successCount > 0) {
            send(src, "[SA] Done. Restart Minecraft to load installed mods.");
        } else {
            send(src, "[SA] All planned mods were already installed.");
        }
        send(src, "[SA] Install manifest saved to config/stutteranalyzer/optimize_last_install.json");
    }

    private static class DownloadResult {
        final String filename;
        final boolean skipped;
        DownloadResult(String fn, boolean skip) { filename = fn; skipped = skip; }
    }

    private static DownloadResult downloadAndVerify(OptimizeMod mod, Path modsDir) throws Exception {
        String fileUrl = mod.resolvedUrl;
        String filename = mod.resolvedFilename;

        if (!fileUrl.startsWith("https://cdn.modrinth.com/")) {
            throw new IllegalArgumentException("Non-whitelisted download URL");
        }
        if (!filename.endsWith(".jar")) {
            throw new IllegalArgumentException("Non-jar filename: " + filename);
        }

        Files.createDirectories(modsDir);
        Path finalPath = modsDir.resolve(filename);

        // Skip if already present and non-empty
        if (Files.exists(finalPath) && Files.size(finalPath) > 0) {
            return new DownloadResult(filename, true);
        }

        // Temp file in config/stutteranalyzer/downloads/
        Path tempDir = modsDir.getParent()
            .resolve("config").resolve("stutteranalyzer").resolve("downloads");
        Files.createDirectories(tempDir);
        Path tempPath = tempDir.resolve(filename + ".tmp");

        LOGGER.info("[SA] Downloading {} from {}", filename, fileUrl);

        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent",
            "StutterAnalyzer/0.2.0 (github.com/Morikemuri/stutter-analyzer)");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(60_000);
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new java.io.IOException("HTTP " + code + " from Modrinth CDN");
        }

        long contentLength = conn.getContentLengthLong();
        if (contentLength > MAX_FILE_SIZE) {
            throw new java.io.IOException("Content-Length too large: " + contentLength);
        }

        MessageDigest md = (mod.resolvedSha512 != null && !mod.resolvedSha512.isEmpty())
            ? MessageDigest.getInstance("SHA-512") : null;

        try {
            try (InputStream is = conn.getInputStream();
                 OutputStream os = Files.newOutputStream(tempPath)) {
                byte[] buf = new byte[8192];
                int read;
                long total = 0;
                while ((read = is.read(buf)) != -1) {
                    total += read;
                    if (total > MAX_FILE_SIZE) {
                        throw new java.io.IOException("Download exceeded max file size");
                    }
                    os.write(buf, 0, read);
                    if (md != null) md.update(buf, 0, read);
                }
            }

            if (!Files.exists(tempPath) || Files.size(tempPath) == 0) {
                throw new java.io.IOException("Downloaded file is empty after write");
            }

            if (md != null) {
                byte[] hash = md.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) sb.append(String.format("%02x", b));
                String computed = sb.toString();
                if (!computed.equalsIgnoreCase(mod.resolvedSha512)) {
                    throw new java.io.IOException("SHA-512 hash mismatch for " + filename);
                }
                LOGGER.info("[SA] Hash verified for {}", filename);
            }

            // Atomic move to mods folder; fall back to regular move if unsupported
            try {
                Files.move(tempPath, finalPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (Exception e) {
            Files.deleteIfExists(tempPath);
            throw e;
        }

        // Final confirmation: file must exist and be non-empty
        if (!Files.exists(finalPath)) {
            throw new java.io.IOException("File missing after move: " + finalPath);
        }
        long finalSize = Files.size(finalPath);
        if (finalSize == 0) {
            Files.deleteIfExists(finalPath);
            throw new java.io.IOException("File is empty after move: " + filename);
        }

        LOGGER.info("[SA] Installed {} ({} bytes) -> {}", filename, finalSize, finalPath);
        return new DownloadResult(filename, false);
    }

    private static void writeManifest(OptimizePlan plan,
                                       List<ManifestEntry> installed,
                                       List<ManifestEntry> failed,
                                       Path gameDir) {
        try {
            Path configDir = gameDir.resolve("config").resolve("stutteranalyzer");
            Files.createDirectories(configDir);
            Path manifestFile = configDir.resolve("optimize_last_install.json");

            JsonObject root = new JsonObject();
            root.addProperty("timestamp", java.time.Instant.now().toString());
            root.addProperty("minecraft_version", plan.mcVersion);
            root.addProperty("loader", plan.loader);
            root.addProperty("risk", plan.risk.name());

            JsonArray installedArr = new JsonArray();
            for (ManifestEntry e : installed) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", e.id);
                obj.addProperty("name", e.name);
                obj.addProperty("source", "modrinth");
                obj.addProperty("file_name", e.filename != null ? e.filename : "");
                obj.addProperty("target_path", e.targetPath != null ? e.targetPath : "");
                obj.addProperty("hash", e.hash != null ? e.hash : "");
                obj.addProperty("status", e.status);
                installedArr.add(obj);
            }
            root.add("installed_files", installedArr);

            JsonArray failedArr = new JsonArray();
            for (ManifestEntry e : failed) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", e.id);
                obj.addProperty("name", e.name);
                obj.addProperty("status", e.status);
                obj.addProperty("reason", e.reason != null ? e.reason : "");
                failedArr.add(obj);
            }
            root.add("failed_files", failedArr);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(manifestFile, gson.toJson(root));
        } catch (Exception e) {
            LOGGER.warn("[SA] Failed to write install manifest: {}", e.getMessage());
        }
    }

    private static void send(CommandSourceStack src, String msg) {
        try {
            src.sendSuccess(() -> CommandFeedback.info(msg), false);
        } catch (Exception e) {
            LOGGER.info("[SA] {}", msg);
        }
    }

    private static class ManifestEntry {
        final String id, name, filename, targetPath, hash, status, reason;
        ManifestEntry(String id, String name, String fn, String tp,
                      String h, String st, String r) {
            this.id = id; this.name = name; this.filename = fn;
            this.targetPath = tp; this.hash = h; this.status = st; this.reason = r;
        }
    }
}
