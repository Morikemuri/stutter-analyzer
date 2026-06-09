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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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
            showInstallWarning(src, plan);
        } else {
            confirmRequestedAt = 0;
            executeInstall(src, plan, modsDir);
        }
    }

    private static void showInstallWarning(CommandSourceStack src, OptimizePlan plan) {
        src.sendFailure(Component.literal(
            "[SA] WARNING: Installing mods can break modpacks, especially large or heavily customized ones."));
        if (plan.risk == OptimizePlan.RiskLevel.HIGH) {
            src.sendFailure(Component.literal(
                "[SA] Risk is HIGH: " + plan.riskReason));
        }
        src.sendSuccess(() -> CommandFeedback.info("[SA] Planned installs:"), false);
        for (OptimizeMod mod : plan.recommended) {
            src.sendSuccess(() -> CommandFeedback.info("  - " + mod.displayName), false);
        }
        src.sendSuccess(() -> CommandFeedback.info(
            "[SA] Files will be downloaded into your mods folder."), false);
        src.sendSuccess(() -> CommandFeedback.info(
            "[SA] Backup your modpack first. Restart required after install."), false);
        src.sendSuccess(() -> CommandFeedback.info(
            "[SA] No guarantee of performance improvement in all cases."), false);
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
        List<InstalledEntry> manifest = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (OptimizeMod mod : plan.recommended) {
            if (mod.resolvedUrl == null || mod.resolvedUrl.isEmpty()) {
                send(src, "[SA] Skipped: " + mod.displayName + " - no compatible download found");
                failCount++;
                continue;
            }
            try {
                java.io.File jar = downloadAndVerify(mod, modsDir);
                if (jar != null) {
                    send(src, "[SA] Installed: " + mod.displayName);
                    manifest.add(new InstalledEntry(
                        mod.displayName, mod.id, jar.getName(),
                        mod.resolvedSha512, mod.resolvedUrl, mod.resolvedFileSize));
                    successCount++;
                } else {
                    send(src, "[SA] Failed: " + mod.displayName + " - download verification failed");
                    failCount++;
                }
            } catch (Exception e) {
                LOGGER.warn("[SA] Install failed for {}: {}", mod.displayName, e.getMessage());
                send(src, "[SA] Failed: " + mod.displayName + " - " + e.getMessage());
                failCount++;
            }
        }

        writeManifest(plan, manifest, modsDir.getParent(), successCount, failCount);

        if (failCount > 0 && successCount > 0) {
            send(src, "[SA] Done with " + failCount + " warning(s). Check latest.log.");
        } else if (failCount > 0 && successCount == 0) {
            send(src, "[SA] All installs failed. Check latest.log for details.");
        } else {
            send(src, "[SA] Done. Restart Minecraft to load installed mods.");
        }
        send(src, "[SA] Install manifest saved to config/stutteranalyzer/optimize_last_install.json");
    }

    private static java.io.File downloadAndVerify(OptimizeMod mod, Path modsDir) throws Exception {
        String fileUrl = mod.resolvedUrl;
        String filename = mod.resolvedFilename;

        if (!fileUrl.startsWith("https://cdn.modrinth.com/")) {
            throw new IllegalArgumentException("Non-whitelisted download URL");
        }
        if (!filename.endsWith(".jar")) {
            throw new IllegalArgumentException("Non-jar filename: " + filename);
        }

        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent",
            "StutterAnalyzer/0.2.0 (github.com/Morikemuri/stutter-analyzer)");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new java.io.IOException("HTTP " + code);
        }

        long contentLength = conn.getContentLengthLong();
        if (contentLength > MAX_FILE_SIZE) {
            throw new java.io.IOException("Content-Length too large: " + contentLength);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            while ((read = is.read(buf)) != -1) {
                total += read;
                if (total > MAX_FILE_SIZE) {
                    throw new java.io.IOException("Download exceeded size limit");
                }
                baos.write(buf, 0, read);
            }
        }
        byte[] data = baos.toByteArray();

        if (mod.resolvedSha512 != null && !mod.resolvedSha512.isEmpty()) {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            String computed = sb.toString();
            if (!computed.equalsIgnoreCase(mod.resolvedSha512)) {
                throw new java.io.IOException("SHA-512 mismatch for " + filename);
            }
        }

        Files.createDirectories(modsDir);
        java.io.File outFile = modsDir.resolve(filename).toFile();
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(data);
        }
        return outFile;
    }

    private static void writeManifest(OptimizePlan plan, List<InstalledEntry> entries,
                                      Path gameDir, int successCount, int failCount) {
        try {
            Path configDir = gameDir.resolve("config/stutteranalyzer");
            Files.createDirectories(configDir);
            Path manifestFile = configDir.resolve("optimize_last_install.json");

            JsonObject manifest = new JsonObject();
            manifest.addProperty("timestamp", System.currentTimeMillis());
            manifest.addProperty("minecraft_version", plan.mcVersion);
            manifest.addProperty("loader", plan.loader);
            manifest.addProperty("previous_mod_count", plan.totalInstalledCount);
            manifest.addProperty("risk_level", plan.risk.name());
            manifest.addProperty("installed_count", successCount);
            manifest.addProperty("failed_count", failCount);

            JsonArray installedArray = new JsonArray();
            for (InstalledEntry e : entries) {
                JsonObject obj = new JsonObject();
                obj.addProperty("display_name", e.displayName);
                obj.addProperty("mod_id", e.modId);
                obj.addProperty("filename", e.filename);
                obj.addProperty("sha512", e.sha512 != null ? e.sha512 : "");
                obj.addProperty("source", "modrinth");
                obj.addProperty("download_url_type", "modrinth_cdn");
                obj.addProperty("file_size", e.fileSize);
                installedArray.add(obj);
            }
            manifest.add("installed", installedArray);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(manifestFile, gson.toJson(manifest));
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

    private static class InstalledEntry {
        final String displayName;
        final String modId;
        final String filename;
        final String sha512;
        final long fileSize;

        InstalledEntry(String dn, String id, String fn, String sh, String url, long sz) {
            displayName = dn;
            modId = id;
            filename = fn;
            sha512 = sh;
            fileSize = sz;
        }
    }
}
