package com.stutteranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.client.DebugHudStatusProvider;
import com.stutteranalyzer.client.F3StatusFormatter;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.MetricsCollector;
import com.stutteranalyzer.submission.SubmissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientCommandRegistrar {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (!SAConfig.INSTANCE.enableCommands.get()) return;
        register(event.getDispatcher(), "stutteranalyzer");
        if (SAConfig.INSTANCE.enableAliasSa.get()) {
            register(event.getDispatcher(), "sa");
        }
    }

    private static int safe(CommandSourceStack src, java.util.concurrent.Callable<Integer> action) {
        try {
            return action.call();
        } catch (Throwable t) {
            SubmissionManager.handleTopLevelSubmitCrash(src, t);
            return 0;
        }
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root)
            // ── bare /sa - quick dashboard ────────────────────────────────────
            .executes(ctx -> CommonCommandLogic.quickDashboard(ctx.getSource()))

            // ── top-level simple commands ─────────────────────────────────────
            .then(Commands.literal("submit")
                .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitLast(ctx.getSource())))
                .then(Commands.literal("preview")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitPreview(ctx.getSource())))
                    .then(Commands.literal("last")
                        .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitPreview(ctx.getSource())))))
                .then(Commands.literal("status")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitStatus(ctx.getSource()))))
                .then(Commands.literal("reset")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitReset(ctx.getSource()))))
                .then(Commands.literal("health")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitHealth(ctx.getSource()))))
                .then(Commands.literal("check")
                    .then(Commands.argument("report_id", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> CommonCommandLogic.submitCheckStatus(
                            ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "report_id")))))
                .then(Commands.literal("config-reset")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitConfigReset(ctx.getSource()))))
                .then(Commands.literal("export-payload")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitExportPayload(ctx.getSource()))))
                .then(Commands.literal("validate-payload")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitValidatePayload(ctx.getSource())))))
            .then(Commands.literal("net")
                .executes(ctx -> CommonCommandLogic.netStatus(ctx.getSource()))
                .then(Commands.literal("health")
                    .executes(ctx -> CommonCommandLogic.netHealth(ctx.getSource())))
                .then(Commands.literal("echo")
                    .executes(ctx -> CommonCommandLogic.netEcho(ctx.getSource()))
                    .then(Commands.literal("java")
                        .executes(ctx -> CommonCommandLogic.netEchoJava(ctx.getSource())))
                    .then(Commands.literal("urlconn")
                        .executes(ctx -> CommonCommandLogic.netEchoUrlConn(ctx.getSource()))))
                .then(Commands.literal("post-minimal")
                    .executes(ctx -> CommonCommandLogic.netPostMinimal(ctx.getSource()))
                    .then(Commands.literal("java")
                        .executes(ctx -> CommonCommandLogic.netPostMinimalJava(ctx.getSource())))
                    .then(Commands.literal("urlconn")
                        .executes(ctx -> CommonCommandLogic.netPostMinimalUrlConn(ctx.getSource()))))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.netStatus(ctx.getSource()))))
            .then(Commands.literal("alerts")
                .executes(ctx -> CommonCommandLogic.alertsStatus(ctx.getSource()))
                .then(Commands.literal("minor")
                    .executes(ctx -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "MINOR")))
                .then(Commands.literal("medium")
                    .executes(ctx -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "MEDIUM")))
                .then(Commands.literal("severe")
                    .executes(ctx -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "SEVERE")))
                .then(Commands.literal("extreme")
                    .executes(ctx -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "EXTREME")))
                .then(Commands.literal("off")
                    .executes(ctx -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "OFF")))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.alertsStatus(ctx.getSource())))
                .then(Commands.literal("test")
                    .executes(ctx -> CommonCommandLogic.alertsTest(ctx.getSource())))
                .then(Commands.literal("cooldown")
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                        .executes(ctx -> CommonCommandLogic.alertsCooldown(
                            ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"))))))
            .then(Commands.literal("yes")
                .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitYes(ctx.getSource()))))
            .then(Commands.literal("cancel")
                .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.cancelLatestPending(ctx.getSource()))))
            .then(Commands.literal("reports")
                .executes(ctx -> CommonCommandLogic.listReports(ctx.getSource())))
            .then(Commands.literal("privacy")
                .executes(ctx -> CommonCommandLogic.showPrivacy(ctx.getSource())))
            .then(Commands.literal("status")
                .executes(ctx -> CommonCommandLogic.showStatus(ctx.getSource())))
            .then(Commands.literal("last")
                .executes(ctx -> CommonCommandLogic.showLast(ctx.getSource())))
            .then(Commands.literal("help")
                .executes(ctx -> CommonCommandLogic.showHelp(ctx.getSource())))

            // ── /sa dev - developer commands ──────────────────────────────────
            .then(Commands.literal("dev")
                .then(Commands.literal("test")
                    .then(Commands.literal("minor")
                        .executes(ctx -> CommonCommandLogic.debugTestMinor(ctx.getSource())))
                    .then(Commands.literal("medium")
                        .executes(ctx -> CommonCommandLogic.debugTestMedium(ctx.getSource())))
                    .then(Commands.literal("severe")
                        .executes(ctx -> CommonCommandLogic.debugTestSevere(ctx.getSource())))
                    .then(Commands.literal("extreme")
                        .executes(ctx -> CommonCommandLogic.debugTestExtreme(ctx.getSource()))))
                .then(Commands.literal("generate-test-report")
                    .executes(ctx -> CommonCommandLogic.generateTestReport(ctx.getSource())))
                .then(Commands.literal("submit-routing")
                    .executes(ctx -> CommonCommandLogic.submitDebugRouting(ctx.getSource())))
                .then(Commands.literal("submit-minimal")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitMinimal(ctx.getSource()))))
                .then(Commands.literal("command-routing")
                    .executes(ctx -> CommonCommandLogic.debugCommandRouting(ctx.getSource())))
                .then(Commands.literal("help")
                    .executes(ctx -> CommonCommandLogic.showDevHelp(ctx.getSource()))))

            // ── /sa admin - admin commands ────────────────────────────────────
            .then(Commands.literal("admin")
                .then(Commands.literal("submit")
                    .then(Commands.literal("mode")
                        .then(Commands.literal("cloudflare")
                            .executes(ctx -> CommonCommandLogic.submitModeCloudflare(ctx.getSource())))
                        .then(Commands.literal("local")
                            .executes(ctx -> CommonCommandLogic.submitModeLocal(ctx.getSource())))
                        .then(Commands.literal("status")
                            .executes(ctx -> CommonCommandLogic.submitModeStatus(ctx.getSource()))))
                    .then(Commands.literal("local")
                        .executes(ctx -> CommonCommandLogic.submitLocalLast(ctx.getSource())))
                    .then(Commands.literal("health")
                        .executes(ctx -> CommonCommandLogic.submitHealth(ctx.getSource())))
                    .then(Commands.literal("status")
                        .executes(ctx -> CommonCommandLogic.submitStatus(ctx.getSource())))))

            // ── overlay subcommands (client-only) ─────────────────────────────
            .then(Commands.literal("overlay")
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.overlay.enabled")), false);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.overlay.disabled")), false);
                        return 1;
                    }))
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.overlay.toggled")), false);
                        return 1;
                    }))
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.overlay.status_off")), false);
                        return 1;
                    })))

            // ── f3 subcommands ─────────────────────────────────────────────────
            .then(Commands.literal("f3")
                .executes(ctx -> {
                    boolean enabled = SAConfig.INSTANCE.debugHudEnabled.get();
                    String current = F3StatusFormatter.format().replaceAll("§.", "");
                    ctx.getSource().sendSuccess(() -> CommandFeedback.row(
                        Component.translatable("stutteranalyzer.row.f3_status_line"),
                        Component.translatable(enabled ? "stutteranalyzer.cmd.f3.enabled" : "stutteranalyzer.cmd.f3.disabled")
                    ), false);
                    if (enabled) ctx.getSource().sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.f3.current", current)), false);
                    return 1;
                })
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        DebugHudStatusProvider.setF3Enabled(true);
                        ctx.getSource().sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.f3.enabled_session")), false);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        DebugHudStatusProvider.setF3Enabled(false);
                        ctx.getSource().sendSuccess(() -> CommandFeedback.success(Component.translatable("stutteranalyzer.cmd.f3.disabled_session")), false);
                        return 1;
                    }))
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        boolean now = !DebugHudStatusProvider.isF3Enabled();
                        DebugHudStatusProvider.setF3Enabled(now);
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info(
                            Component.translatable(now ? "stutteranalyzer.cmd.f3.toggled_on" : "stutteranalyzer.cmd.f3.toggled_off")
                        ), false);
                        return 1;
                    }))
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        boolean enabled = DebugHudStatusProvider.isF3Enabled();
                        String current = F3StatusFormatter.format().replaceAll("§.", "");
                        ctx.getSource().sendSuccess(() -> CommandFeedback.row(
                            Component.translatable("stutteranalyzer.row.f3_status_line"),
                            Component.translatable(enabled ? "stutteranalyzer.cmd.f3.enabled" : "stutteranalyzer.cmd.f3.disabled")
                        ), false);
                        if (enabled) ctx.getSource().sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.f3.current", current)), false);
                        return 1;
                    })))
            // ── selfcheck / test (client side has full info) ──────────────────
            .then(Commands.literal("selfcheck")
                .executes(ctx -> CommonCommandLogic.selfCheck(ctx.getSource())))
            .then(Commands.literal("test")
                .executes(ctx -> CommonCommandLogic.selfCheck(ctx.getSource())))

            // ── client subcommands ─────────────────────────────────────────────
            .then(Commands.literal("client")
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.showStatus(ctx.getSource())))
                .then(Commands.literal("last")
                    .executes(ctx -> CommonCommandLogic.showLast(ctx.getSource())))
                .then(Commands.literal("report")
                    .executes(ctx -> CommonCommandLogic.generateReport(ctx.getSource())))
                .then(Commands.literal("export")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!CommandPermissionHelper.canExportOwnClientReport(src)) {
                            src.sendFailure(CommandFeedback.noPermission());
                            return 0;
                        }
                        return CommonCommandLogic.exportReport(src);
                    }))
                .then(Commands.literal("submit")
                    .then(Commands.literal("last")
                        .executes(ctx -> CommonCommandLogic.submitLast(ctx.getSource())))
                    .then(Commands.literal("prepare")
                        .then(Commands.literal("last")
                            .executes(ctx -> CommonCommandLogic.submitPrepareLast(ctx.getSource()))))
                    .then(Commands.literal("yes")
                        .executes(ctx -> CommonCommandLogic.submitYes(ctx.getSource())))
                    .then(Commands.literal("send")
                        .executes(ctx -> CommonCommandLogic.submitSend(ctx.getSource())))
                    .then(Commands.literal("confirm")
                        .executes(ctx -> CommonCommandLogic.submitConfirmLast(ctx.getSource()))
                        .then(Commands.literal("last")
                            .executes(ctx -> CommonCommandLogic.submitConfirmLast(ctx.getSource())))
                        .then(Commands.argument("prepared_id", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(ctx -> CommonCommandLogic.submitConfirm(
                                ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "prepared_id")))))
                    .then(Commands.literal("cancel")
                        .then(Commands.argument("prepared_id", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(ctx -> CommonCommandLogic.submitCancelPrepared(
                                ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "prepared_id")))))
                    .then(Commands.literal("local")
                        .then(Commands.literal("last")
                            .executes(ctx -> CommonCommandLogic.submitLocalLast(ctx.getSource()))))
                    .then(Commands.literal("status")
                        .executes(ctx -> CommonCommandLogic.submitStatus(ctx.getSource())))
                    .then(Commands.literal("health")
                        .executes(ctx -> CommonCommandLogic.submitHealth(ctx.getSource())))
                    .then(Commands.literal("debug-routing")
                        .executes(ctx -> CommonCommandLogic.submitDebugRouting(ctx.getSource())))
                    .then(Commands.literal("preview")
                        .executes(ctx -> CommonCommandLogic.submitPreview(ctx.getSource()))
                        .then(Commands.literal("last")
                            .executes(ctx -> CommonCommandLogic.submitPreview(ctx.getSource()))))
                    .then(Commands.literal("mode")
                        .then(Commands.literal("cloudflare")
                            .executes(ctx -> CommonCommandLogic.submitModeCloudflare(ctx.getSource())))
                        .then(Commands.literal("local")
                            .executes(ctx -> CommonCommandLogic.submitModeLocal(ctx.getSource())))
                        .then(Commands.literal("status")
                            .executes(ctx -> CommonCommandLogic.submitModeStatus(ctx.getSource()))))))

            // ── verbose mode ───────────────────────────────────────────────────
            .then(Commands.literal("verbose")
                .executes(ctx -> CommonCommandLogic.verboseStatus(ctx.getSource()))
                .then(Commands.literal("on")
                    .executes(ctx -> CommonCommandLogic.verboseOn(ctx.getSource())))
                .then(Commands.literal("off")
                    .executes(ctx -> CommonCommandLogic.verboseOff(ctx.getSource())))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.verboseStatus(ctx.getSource()))))
            .then(Commands.literal("notifications")
                .then(Commands.literal("minor")
                    .then(Commands.literal("on")
                        .executes(ctx -> CommonCommandLogic.verboseOn(ctx.getSource())))
                    .then(Commands.literal("off")
                        .executes(ctx -> CommonCommandLogic.verboseOff(ctx.getSource()))))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.verboseStatus(ctx.getSource()))))

            // ── debug (client-side freeze injection) ──────────────────────────
            .then(Commands.literal("debug")
                .then(Commands.literal("freeze")
                    .then(Commands.literal("client")
                        .then(Commands.argument("milliseconds", IntegerArgumentType.integer(1, 30000))
                            .executes(ctx -> {
                                CommandSourceStack src = ctx.getSource();
                                if (!CommandPermissionHelper.canUseDebug(src)) {
                                    src.sendFailure(CommandFeedback.debugDisabled()); return 0;
                                }
                                int ms = IntegerArgumentType.getInteger(ctx, "milliseconds");
                                src.sendSuccess(() -> CommandFeedback.debug("Freezing client thread for " + ms + " ms..."), true);
                                try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
                                FreezeDetector.onClientFrameSpike(ms, MetricsCollector.eventBuffer(), false);
                                src.sendSuccess(() -> CommandFeedback.debug("Client freeze injected (" + ms + " ms)."), true);
                                return 1;
                            }))))
                .then(Commands.literal("test")
                    .then(Commands.literal("minor")
                        .executes(ctx -> CommonCommandLogic.debugTestMinor(ctx.getSource())))
                    .then(Commands.literal("medium")
                        .executes(ctx -> CommonCommandLogic.debugTestMedium(ctx.getSource())))
                    .then(Commands.literal("severe")
                        .executes(ctx -> CommonCommandLogic.debugTestSevere(ctx.getSource())))
                    .then(Commands.literal("extreme")
                        .executes(ctx -> CommonCommandLogic.debugTestExtreme(ctx.getSource()))))
                .then(Commands.literal("visibility-test")
                    .executes(ctx -> CommonCommandLogic.debugVisibilityTest(ctx.getSource())))
                .then(Commands.literal("command-routing")
                    .executes(ctx -> CommonCommandLogic.debugCommandRouting(ctx.getSource()))))

            // ── quiet mode ────────────────────────────────────────────────────
            .then(Commands.literal("quiet")
                .executes(ctx -> CommonCommandLogic.quietStatus(ctx.getSource()))
                .then(Commands.literal("on")
                    .executes(ctx -> CommonCommandLogic.quietOn(ctx.getSource())))
                .then(Commands.literal("off")
                    .executes(ctx -> CommonCommandLogic.quietOff(ctx.getSource())))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.quietStatus(ctx.getSource()))))

            // ── version ───────────────────────────────────────────────────────
            .then(Commands.literal("version")
                .executes(ctx -> CommonCommandLogic.showVersion(ctx.getSource())))

            // ── update ────────────────────────────────────────────────────────
            .then(Commands.literal("update")
                .executes(ctx -> CommonCommandLogic.updateStatus(ctx.getSource()))
                .then(Commands.literal("check")
                    .executes(ctx -> CommonCommandLogic.updateCheck(ctx.getSource())))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.updateStatus(ctx.getSource())))
                .then(Commands.literal("link")
                    .executes(ctx -> CommonCommandLogic.updateLink(ctx.getSource()))))
        );
    }
}
