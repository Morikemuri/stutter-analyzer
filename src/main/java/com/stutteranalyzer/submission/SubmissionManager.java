package com.stutteranalyzer.submission;

import com.stutteranalyzer.StutterAnalyzerMod;
import com.stutteranalyzer.command.CommandFeedback;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.crash.CrashEvent;
import com.stutteranalyzer.crash.PreviousCrashImporter;
import com.stutteranalyzer.guard.EmergencyGuardManager;
import com.stutteranalyzer.guard.EmergencyGuardReport;
import com.stutteranalyzer.report.FreezeReport;
import com.stutteranalyzer.report.ReportWriter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubmissionManager {

    // Upload goes off-thread. Blocking the game for a network request is not an option.
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "stutteranalyzer-gist-uploader");
        t.setDaemon(true);
        return t;
    });

    public static int submitLast(CommandSourceStack src) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        FreezeReport report = ReportWriter.lastReport();
        if (report == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.no_report")), false);
            return 1;
        }
        return doSubmit(src, report.reportId, report.toMarkdown());
    }

    public static int submitById(CommandSourceStack src, String reportId) {
        if (!SAConfig.INSTANCE.enableManualSubmission.get()) {
            src.sendFailure(CommandFeedback.error(Component.translatable("stutteranalyzer.submit.disabled")));
            return 0;
        }
        FreezeReport last = ReportWriter.lastReport();
        if (last != null && last.reportId.equals(reportId)) {
            return doSubmit(src, last.reportId, last.toMarkdown());
        }
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.not_found", reportId)), false);
        return 1;
    }

    public static int submitCrashLast(CommandSourceStack src) {
        CrashEvent ce = PreviousCrashImporter.last();
        if (ce == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.no_crash")), false);
            return 1;
        }
        return doSubmit(src, ce.crashId, buildCrashMarkdown(ce));
    }

    public static int submitGuardLast(CommandSourceStack src) {
        EmergencyGuardReport rep = EmergencyGuardManager.lastReport();
        if (rep == null) {
            src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.no_guard")), false);
            return 1;
        }
        return doSubmit(src, rep.guardId, rep.toMarkdown());
    }

    private static int doSubmit(CommandSourceStack src, String id, String markdown) {
        src.sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.submit.warning")), false);
        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.uploading")), false);

        Path gameDir = FMLEnvironment.dist == Dist.CLIENT
            ? clientGameDir()
            : FMLPaths.GAMEDIR.get();

        EXECUTOR.submit(() -> {
            String logSnippet = LogSnippetExtractor.extract(gameDir);
            GistUploader.Result result = GistUploader.upload(id, markdown, logSnippet);

            if (result.success()) {
                String gistUrl  = result.gistUrl();
                String issueUrl = buildIssueUrl(id, gistUrl);

                src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.submit.uploaded", gistUrl)), false);
                src.sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.submit.opening_browser")), false);

                openBrowser(issueUrl);
            } else {
                String errMsg     = result.error();
                String fallbackUrl = SAConfig.INSTANCE.githubIssueUrl.get();
                src.sendSuccess(() -> CommandFeedback.error(Component.translatable("stutteranalyzer.submit.failed", errMsg)), false);
                src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.submit.fallback", fallbackUrl)), false);
            }
        });

        return 1;
    }

    private static String buildIssueUrl(String reportId, String gistUrl) {
        String base  = SAConfig.INSTANCE.githubIssueUrl.get();
        String title = enc("Unknown Freeze Report [" + reportId + "]");
        String body  = enc(
            "**Freeze report (Gist):** " + gistUrl + "\n\n" +
            "**What were you doing when the freeze occurred?**\n\n" +
            "(describe here)\n\n" +
            "---\n*Submitted via StutterAnalyzer /sa submit last*"
        );
        return base + "?title=" + title + "&body=" + body;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Opens a URL in the system browser. Client-only. No-op on dedicated server. */
    private static void openBrowser(String url) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        try {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                try {
                    net.minecraft.Util.getPlatform().openUri(new URI(url));
                } catch (Exception e) {
                    StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Failed to open browser: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            StutterAnalyzerMod.LOGGER.warn("[StutterAnalyzer] Failed to schedule browser open: {}", e.getMessage());
        }
    }

    private static Path clientGameDir() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath();
    }

    private static String buildCrashMarkdown(CrashEvent ce) {
        return "# Crash Report Import\n\n" +
            "**ID:** " + ce.crashId + "\n" +
            "**Timestamp:** " + ce.timestamp + "\n" +
            "**Type:** " + ce.crashType + "\n" +
            "**Summary:** " + ce.summary + "\n" +
            (ce.hasKnownPattern()
                ? "\n**Known Pattern:** " + ce.bestMatch().patternId +
                  " (" + ce.bestMatch().confidencePct() + "% confidence)\n"
                : "\n**Pattern:** Unknown\n");
    }
}
