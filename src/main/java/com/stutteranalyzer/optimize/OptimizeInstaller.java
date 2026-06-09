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

    // Async scan state
    private static volatile boolean scanning = false;
    private static volatile java.util.List<net.minecraft.network.chat.Component> pendingScanMessages = null;

    public static boolean isScanning() { return scanning; }

    public static void startScan() {
        scanning = true;
        pendingScanMessages = null;
    }

    public static void completeScan(java.util.List<net.minecraft.network.chat.Component> messages) {
        pendingScanMessages = messages;
        scanning = false;
    }

    public static java.util.List<net.minecraft.network.chat.Component> consumePendingScanMessages() {
        java.util.List<net.minecraft.network.chat.Component> msgs = pendingScanMessages;
        if (msgs != null) pendingScanMessages = null;
        return msgs;
    }

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
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.optimize.no_plan")), false);
            return;
        }
        if (plan.isExpired(PLAN_MAX_AGE_MS)) {
            currentPlan = null;
            confirmRequestedAt = 0;
            src.sendSuccess(() -> CommandFeedback.info(
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.optimize.plan_expired")), false);
            return;
        }
        if (plan.isEmpty()) {
            src.sendSuccess(() -> CommandFeedback.info(
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.optimize.nothing_to_install")), false);
            return;
        }
        if (modsDir == null) {
            src.sendSuccess(() -> CommandFeedback.info(
                net.minecraft.network.chat.Component.translatable("stutteranalyzer.optimize.no_mods_dir")), false);
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
        src.sendFailure(Component.translatable("stutteranalyzer.optimize.warning.line1"));
        src.sendFailure(Component.translatable("stutteranalyzer.optimize.warning.backup"));

        int shown = Math.min(plan.recommended.size(), 5);
        String nameList = plan.recommended.subList(0, shown).stream()
            .map(m -> m.displayName).collect(java.util.stream.Collectors.joining(", "));
        int remaining = plan.recommended.size() - shown;
        String plannedLine = remaining > 0 ? nameList + " +" + remaining : nameList;
        send(src, Component.translatable("stutteranalyzer.optimize.warning.planned", plannedLine));

        Component confirmBtn = Component.translatable("stutteranalyzer.optimize.btn.confirm")
            .withStyle(s -> s
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/sa optimize install"))
                .withUnderlined(true)
                .withColor(net.minecraft.ChatFormatting.GOLD));
        send(src, confirmBtn);
    }

    private static void executeInstall(CommandSourceStack src, OptimizePlan plan, Path modsDir) {
        send(src, Component.translatable("stutteranalyzer.optimize.install.starting", plan.recommended.size()));
        Thread worker = new Thread(() -> doInstall(src, plan, modsDir), "SA-OptimizeInstall");
        worker.setDaemon(true);
        worker.start();
    }

    private static void doInstall(CommandSourceStack src, OptimizePlan plan, Path modsDir) {
        List<ManifestEntry> installedList = new ArrayList<>();
        List<ManifestEntry> failedList = new ArrayList<>();
        int successCount = 0;
        int alreadyCount = 0;
        int failCount = 0;

        for (OptimizeMod mod : plan.recommended) {
            if (mod.resolvedUrl == null || mod.resolvedUrl.isEmpty()) {
                LOGGER.info("[SA] Safety net: {} has no resolvedUrl, skipping", mod.displayName);
                continue;
            }

            try {
                DownloadResult result = downloadAndVerify(mod, modsDir);
                if (result.skipped) {
                    send(src, Component.translatable("stutteranalyzer.optimize.install.already", mod.displayName));
                    installedList.add(new ManifestEntry(mod.id, mod.displayName,
                        result.filename, modsDir.resolve(result.filename).toString(),
                        mod.resolvedSha512, "already_present", null));
                    alreadyCount++;
                } else {
                    send(src, Component.translatable("stutteranalyzer.optimize.install.ok", mod.displayName));
                    installedList.add(new ManifestEntry(mod.id, mod.displayName,
                        result.filename, modsDir.resolve(result.filename).toString(),
                        mod.resolvedSha512, "installed", null));
                    successCount++;
                }
            } catch (Exception e) {
                LOGGER.warn("[SA] Install failed for {}: {}", mod.displayName, e.getMessage(), e);
                send(src, Component.translatable("stutteranalyzer.optimize.install.fail_item", mod.displayName));
                failedList.add(new ManifestEntry(mod.id, mod.displayName,
                    null, null, null, "failed", e.getMessage()));
                failCount++;
            }
        }

        writeManifest(plan, installedList, failedList, modsDir.getParent());

        if (successCount > 0) {
            send(src, Component.translatable("stutteranalyzer.optimize.install.done", successCount, alreadyCount, failCount));
        } else if (alreadyCount > 0 || failCount > 0) {
            send(src, Component.translatable("stutteranalyzer.optimize.install.done_no_new", alreadyCount, failCount));
        } else {
            send(src, Component.translatable("stutteranalyzer.optimize.install.done_fail"));
        }
        if (failCount > 0) {
            send(src, Component.translatable("stutteranalyzer.optimize.install.fail_hint"));
        }
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

    private static void send(CommandSourceStack src, Component comp) {
        try {
            src.sendSuccess(() -> CommandFeedback.info(comp), false);
        } catch (Exception e) {
            LOGGER.info("[SA] {}", comp.getString());
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
