package com.stutteranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.stutteranalyzer.config.SAConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerCommandRegistrar {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!SAConfig.INSTANCE.enableCommands.get()) return;
        register(event.getDispatcher(), "stutteranalyzer");
        if (SAConfig.INSTANCE.enableAliasSa.get()) {
            register(event.getDispatcher(), "sa");
        }
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root)

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

            // ── quiet mode ────────────────────────────────────────────────────
            .then(Commands.literal("quiet")
                .executes(ctx -> CommonCommandLogic.quietStatus(ctx.getSource()))
                .then(Commands.literal("on")
                    .executes(ctx -> CommonCommandLogic.quietOn(ctx.getSource())))
                .then(Commands.literal("off")
                    .executes(ctx -> CommonCommandLogic.quietOff(ctx.getSource())))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.quietStatus(ctx.getSource()))))

            // ── status / health / last / help / selfcheck (level 0) ──────────
            .then(Commands.literal("status")
                .executes(ctx -> CommonCommandLogic.showStatus(ctx.getSource())))
            .then(Commands.literal("health")
                .executes(ctx -> CommonCommandLogic.showHealth(ctx.getSource())))
            .then(Commands.literal("last")
                .executes(ctx -> CommonCommandLogic.showLast(ctx.getSource())))
            .then(Commands.literal("help")
                .executes(ctx -> CommonCommandLogic.showHelp(ctx.getSource())))
            .then(Commands.literal("selfcheck")
                .executes(ctx -> CommonCommandLogic.selfCheck(ctx.getSource())))
            .then(Commands.literal("test")
                .executes(ctx -> CommonCommandLogic.selfCheck(ctx.getSource())))

            // ── report / export (level 0) ────────────────────────────────────
            .then(Commands.literal("report")
                .executes(ctx -> CommonCommandLogic.generateReport(ctx.getSource())))
            .then(Commands.literal("export")
                .executes(ctx -> CommonCommandLogic.exportReport(ctx.getSource())))

            // ── list (level 2 for server-wide) ──────────────────────────────
            .then(Commands.literal("list")
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    if (!CommandPermissionHelper.canViewServerReports(src)) {
                        src.sendFailure(CommandFeedback.noPermission()); return 0;
                    }
                    return CommonCommandLogic.listReports(src);
                })
                .then(Commands.literal("unknown")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!CommandPermissionHelper.canViewServerReports(src)) {
                            src.sendFailure(CommandFeedback.noPermission()); return 0;
                        }
                        return CommonCommandLogic.listUnknownReports(src);
                    })))
            .then(Commands.literal("show")
                .then(Commands.argument("report_id", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!CommandPermissionHelper.canViewServerReports(src)) {
                            src.sendFailure(CommandFeedback.noPermission()); return 0;
                        }
                        return CommonCommandLogic.showReport(src, StringArgumentType.getString(ctx, "report_id"));
                    })))

            // ── delete (level 3) ─────────────────────────────────────────────
            .then(Commands.literal("delete")
                .then(Commands.argument("report_id", StringArgumentType.word())
                    .executes(ctx -> CommonCommandLogic.deleteReport(
                        ctx.getSource(), StringArgumentType.getString(ctx, "report_id")))))

            // ── submit ───────────────────────────────────────────────────────
            .then(Commands.literal("submit")
                .then(Commands.literal("last")
                    .executes(ctx -> CommonCommandLogic.submitLast(ctx.getSource())))
                .then(Commands.literal("prepare")
                    .then(Commands.literal("last")
                        .executes(ctx -> CommonCommandLogic.submitPrepareLast(ctx.getSource())))
                    .then(Commands.argument("report_id", StringArgumentType.word())
                        .executes(ctx -> CommonCommandLogic.submitReport(
                            ctx.getSource(), StringArgumentType.getString(ctx, "report_id")))))
                .then(Commands.literal("yes")
                    .executes(ctx -> CommonCommandLogic.submitYes(ctx.getSource())))
                .then(Commands.literal("send")
                    .executes(ctx -> CommonCommandLogic.submitSend(ctx.getSource())))
                .then(Commands.literal("confirm")
                    .then(Commands.literal("last")
                        .executes(ctx -> CommonCommandLogic.submitConfirmLast(ctx.getSource())))
                    .then(Commands.argument("prepared_id", StringArgumentType.greedyString())
                        .executes(ctx -> CommonCommandLogic.submitConfirm(
                            ctx.getSource(), StringArgumentType.getString(ctx, "prepared_id")))))
                .then(Commands.literal("cancel")
                    .then(Commands.argument("prepared_id", StringArgumentType.greedyString())
                        .executes(ctx -> CommonCommandLogic.submitCancelPrepared(
                            ctx.getSource(), StringArgumentType.getString(ctx, "prepared_id")))))
                .then(Commands.literal("local")
                    .then(Commands.literal("last")
                        .executes(ctx -> CommonCommandLogic.submitLocalLast(ctx.getSource()))))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.submitStatus(ctx.getSource())))
                .then(Commands.literal("health")
                    .executes(ctx -> CommonCommandLogic.submitHealth(ctx.getSource())))
                .then(Commands.literal("debug-routing")
                    .executes(ctx -> CommonCommandLogic.submitDebugRouting(ctx.getSource())))
                .then(Commands.literal("mode")
                    .then(Commands.literal("cloudflare")
                        .executes(ctx -> CommonCommandLogic.submitModeCloudflare(ctx.getSource())))
                    .then(Commands.literal("local")
                        .executes(ctx -> CommonCommandLogic.submitModeLocal(ctx.getSource())))
                    .then(Commands.literal("status")
                        .executes(ctx -> CommonCommandLogic.submitModeStatus(ctx.getSource()))))
                .then(Commands.literal("crash")
                    .then(Commands.literal("last")
                        .executes(ctx -> CommonCommandLogic.submitCrashLast(ctx.getSource())))
                    .then(Commands.argument("crash_id", StringArgumentType.word())
                        .executes(ctx -> CommonCommandLogic.submitCrash(
                            ctx.getSource(), StringArgumentType.getString(ctx, "crash_id")))))
                .then(Commands.literal("guard")
                    .then(Commands.literal("last")
                        .executes(ctx -> CommonCommandLogic.submitGuardLast(ctx.getSource())))
                    .then(Commands.argument("guard_id", StringArgumentType.word())
                        .executes(ctx -> CommonCommandLogic.submitGuard(
                            ctx.getSource(), StringArgumentType.getString(ctx, "guard_id")))))
                .then(Commands.argument("report_id", StringArgumentType.word())
                    .executes(ctx -> CommonCommandLogic.submitReport(
                        ctx.getSource(), StringArgumentType.getString(ctx, "report_id")))))

            // ── config (level 3) ─────────────────────────────────────────────
            .then(Commands.literal("config")
                .then(Commands.literal("reload")
                    .executes(ctx -> CommonCommandLogic.reloadConfig(ctx.getSource()))))

            // ── crash commands ───────────────────────────────────────────────
            .then(Commands.literal("crash")
                .then(Commands.literal("last")
                    .executes(ctx -> CommonCommandLogic.crashLast(ctx.getSource())))
                .then(Commands.literal("list")
                    .executes(ctx -> CommonCommandLogic.crashList(ctx.getSource())))
                .then(Commands.literal("show")
                    .then(Commands.argument("crash_id", StringArgumentType.word())
                        .executes(ctx -> CommonCommandLogic.crashShow(
                            ctx.getSource(), StringArgumentType.getString(ctx, "crash_id")))))
                .then(Commands.literal("export")
                    .then(Commands.argument("crash_id", StringArgumentType.word())
                        .executes(ctx -> CommonCommandLogic.crashExport(
                            ctx.getSource(), StringArgumentType.getString(ctx, "crash_id"))))))

            // ── guard commands ───────────────────────────────────────────────
            .then(Commands.literal("guard")
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.guardStatus(ctx.getSource())))
                .then(Commands.literal("list")
                    .executes(ctx -> CommonCommandLogic.guardList(ctx.getSource())))
                .then(Commands.literal("info")
                    .then(Commands.argument("guard_id", StringArgumentType.word())
                        .executes(ctx -> CommonCommandLogic.guardInfo(
                            ctx.getSource(), StringArgumentType.getString(ctx, "guard_id")))))
                .then(Commands.literal("enable")
                    .then(Commands.argument("guard_id", StringArgumentType.word())
                        .executes(ctx -> CommonCommandLogic.guardEnable(
                            ctx.getSource(), StringArgumentType.getString(ctx, "guard_id")))))
                .then(Commands.literal("disable")
                    .then(Commands.argument("guard_id", StringArgumentType.word())
                        .executes(ctx -> CommonCommandLogic.guardDisable(
                            ctx.getSource(), StringArgumentType.getString(ctx, "guard_id")))))
                .then(Commands.literal("report")
                    .then(Commands.literal("last")
                        .executes(ctx -> CommonCommandLogic.guardReportLast(ctx.getSource())))))

            // ── debug (level 4, requires debug=true in config) ───────────────
            .then(Commands.literal("debug")
                .then(Commands.literal("on")
                    .executes(ctx -> CommonCommandLogic.enableDebug(ctx.getSource())))
                .then(Commands.literal("off")
                    .executes(ctx -> CommonCommandLogic.disableDebug(ctx.getSource())))
                .then(Commands.literal("freeze")
                    .then(Commands.literal("server")
                        .then(Commands.argument("milliseconds", IntegerArgumentType.integer(1, 30000))
                            .executes(ctx -> {
                                CommandSourceStack src = ctx.getSource();
                                if (!CommandPermissionHelper.canUseDebug(src)) {
                                    src.sendFailure(CommandFeedback.debugDisabled()); return 0;
                                }
                                int ms = IntegerArgumentType.getInteger(ctx, "milliseconds");
                                src.sendSuccess(() -> CommandFeedback.debug("Freezing server thread for " + ms + " ms..."), true);
                                try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
                                src.sendSuccess(() -> CommandFeedback.debug("Server thread unfreeze complete."), true);
                                return 1;
                            })))
                    .then(Commands.literal("client")
                        .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })))
                .then(Commands.literal("spike")
                    .then(Commands.literal("server")
                        .then(Commands.argument("milliseconds", IntegerArgumentType.integer(1, 30000))
                            .executes(ctx -> {
                                CommandSourceStack src = ctx.getSource();
                                if (!CommandPermissionHelper.canUseDebug(src)) {
                                    src.sendFailure(CommandFeedback.debugDisabled()); return 0;
                                }
                                int ms = IntegerArgumentType.getInteger(ctx, "milliseconds");
                                src.sendSuccess(() -> CommandFeedback.debug("Simulating server spike (" + ms + " ms)..."), true);
                                try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
                                return 1;
                            }))))
                .then(Commands.literal("gc")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!CommandPermissionHelper.canUseDebug(src)) {
                            src.sendFailure(CommandFeedback.debugDisabled()); return 0;
                        }
                        System.gc();
                        src.sendSuccess(() -> CommandFeedback.debug("GC hint sent."), true);
                        return 1;
                    }))
                .then(Commands.literal("generate-test-report")
                    .executes(ctx -> CommonCommandLogic.generateTestReport(ctx.getSource())))
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

            // ── verbose mode ─────────────────────────────────────────────────
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

            // ── server subcommands ───────────────────────────────────────────
            .then(Commands.literal("server")
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.showStatus(ctx.getSource())))
                .then(Commands.literal("health")
                    .executes(ctx -> CommonCommandLogic.showHealth(ctx.getSource())))
                .then(Commands.literal("last")
                    .executes(ctx -> CommonCommandLogic.showLast(ctx.getSource())))
                .then(Commands.literal("report")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!CommandPermissionHelper.canViewServerReports(src)) {
                            src.sendFailure(CommandFeedback.noPermission()); return 0;
                        }
                        return CommonCommandLogic.generateReport(src);
                    }))
                .then(Commands.literal("export")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!CommandPermissionHelper.canViewServerReports(src)) {
                            src.sendFailure(CommandFeedback.noPermission()); return 0;
                        }
                        return CommonCommandLogic.exportReport(src);
                    }))
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!CommandPermissionHelper.canViewServerReports(src)) {
                            src.sendFailure(CommandFeedback.noPermission()); return 0;
                        }
                        return CommonCommandLogic.listReports(src);
                    })
                    .then(Commands.literal("unknown")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            if (!CommandPermissionHelper.canViewServerReports(src)) {
                                src.sendFailure(CommandFeedback.noPermission()); return 0;
                            }
                            return CommonCommandLogic.listUnknownReports(src);
                        })))
                .then(Commands.literal("show")
                    .then(Commands.argument("report_id", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            if (!CommandPermissionHelper.canViewServerReports(src)) {
                                src.sendFailure(CommandFeedback.noPermission()); return 0;
                            }
                            return CommonCommandLogic.showReport(src, StringArgumentType.getString(ctx, "report_id"));
                        })))
                .then(Commands.literal("submit")
                    .then(Commands.literal("last")
                        .executes(ctx -> CommonCommandLogic.submitLast(ctx.getSource())))
                    .then(Commands.argument("report_id", StringArgumentType.word())
                        .executes(ctx -> CommonCommandLogic.submitReport(
                            ctx.getSource(), StringArgumentType.getString(ctx, "report_id")))))
                .then(Commands.literal("config")
                    .then(Commands.literal("reload")
                        .executes(ctx -> CommonCommandLogic.reloadConfig(ctx.getSource())))))

            // ── f3 (client-only, fails gracefully on server) ─────────────────
            .then(Commands.literal("f3")
                .executes(ctx -> CommonCommandLogic.f3Status(ctx.getSource()))
                .then(Commands.literal("on")
                    .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("off")
                    .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("toggle")
                    .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.f3Status(ctx.getSource()))))

            // ── overlay and client: fail gracefully on server ─────────────────
            .then(Commands.literal("overlay")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })
                .then(Commands.literal("on")    .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("off")   .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("toggle").executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("status").executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })))
            .then(Commands.literal("client")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })
                .then(Commands.literal("last")  .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("report").executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("export").executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("submit")
                    .then(Commands.literal("last").executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))))
        );
    }
}
