package com.stutteranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class FabricCommandRegistrar {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root)

            .executes(c -> CommonCommandLogic.quickDashboard(c.getSource()))

            .then(Commands.literal("yes")
                .executes(c -> CommonCommandLogic.submitYes(c.getSource())))
            .then(Commands.literal("cancel")
                .executes(c -> CommonCommandLogic.cancelLatestPending(c.getSource())))
            .then(Commands.literal("reports")
                .executes(c -> CommonCommandLogic.listReports(c.getSource())))
            .then(Commands.literal("privacy")
                .executes(c -> CommonCommandLogic.showPrivacy(c.getSource())))

            .then(Commands.literal("dev")
                .then(Commands.literal("test")
                    .then(Commands.literal("minor")
                        .executes(c -> CommonCommandLogic.debugTestMinor(c.getSource())))
                    .then(Commands.literal("medium")
                        .executes(c -> CommonCommandLogic.debugTestMedium(c.getSource())))
                    .then(Commands.literal("severe")
                        .executes(c -> CommonCommandLogic.debugTestSevere(c.getSource())))
                    .then(Commands.literal("extreme")
                        .executes(c -> CommonCommandLogic.debugTestExtreme(c.getSource()))))
                .then(Commands.literal("generate-test-report")
                    .executes(c -> CommonCommandLogic.generateTestReport(c.getSource())))
                .then(Commands.literal("submit-routing")
                    .executes(c -> CommonCommandLogic.submitDebugRouting(c.getSource())))
                .then(Commands.literal("command-routing")
                    .executes(c -> CommonCommandLogic.debugCommandRouting(c.getSource())))
                .then(Commands.literal("help")
                    .executes(c -> CommonCommandLogic.showDevHelp(c.getSource()))))

            .then(Commands.literal("admin")
                .then(Commands.literal("submit")
                    .then(Commands.literal("mode")
                        .then(Commands.literal("cloudflare")
                            .executes(c -> CommonCommandLogic.submitModeCloudflare(c.getSource())))
                        .then(Commands.literal("local")
                            .executes(c -> CommonCommandLogic.submitModeLocal(c.getSource())))
                        .then(Commands.literal("status")
                            .executes(c -> CommonCommandLogic.submitModeStatus(c.getSource()))))
                    .then(Commands.literal("local")
                        .executes(c -> CommonCommandLogic.submitLocalLast(c.getSource())))
                    .then(Commands.literal("health")
                        .executes(c -> CommonCommandLogic.submitHealth(c.getSource())))
                    .then(Commands.literal("status")
                        .executes(c -> CommonCommandLogic.submitStatus(c.getSource())))))

            .then(Commands.literal("version")
                .executes(c -> CommonCommandLogic.showVersion(c.getSource()))
                .then(Commands.literal("debug")
                    .executes(c -> CommonCommandLogic.showVersionDebug(c.getSource()))))

            .then(Commands.literal("update")
                .executes(c -> CommonCommandLogic.updateStatus(c.getSource()))
                .then(Commands.literal("check")
                    .executes(c -> CommonCommandLogic.updateCheck(c.getSource())))
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.updateStatus(c.getSource())))
                .then(Commands.literal("link")
                    .executes(c -> CommonCommandLogic.updateLink(c.getSource()))))

            .then(Commands.literal("quiet")
                .executes(c -> CommonCommandLogic.quietStatus(c.getSource()))
                .then(Commands.literal("on")
                    .executes(c -> CommonCommandLogic.quietOn(c.getSource())))
                .then(Commands.literal("off")
                    .executes(c -> CommonCommandLogic.quietOff(c.getSource())))
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.quietStatus(c.getSource()))))

            .then(Commands.literal("status")
                .executes(c -> CommonCommandLogic.showStatus(c.getSource())))
            .then(Commands.literal("health")
                .executes(c -> CommonCommandLogic.showHealth(c.getSource())))
            .then(Commands.literal("last")
                .executes(c -> CommonCommandLogic.showLast(c.getSource())))
            .then(Commands.literal("help")
                .executes(c -> CommonCommandLogic.showHelp(c.getSource())))
            .then(Commands.literal("selfcheck")
                .executes(c -> CommonCommandLogic.selfCheck(c.getSource())))
            .then(Commands.literal("test")
                .executes(c -> CommonCommandLogic.selfCheck(c.getSource())))

            .then(Commands.literal("report")
                .executes(c -> CommonCommandLogic.generateReport(c.getSource())))
            .then(Commands.literal("export")
                .executes(c -> CommonCommandLogic.exportReport(c.getSource())))

            .then(Commands.literal("list")
                .executes(c -> {
                    CommandSourceStack src = c.getSource();
                    if (!CommandPermissionHelper.canViewServerReports(src)) {
                        src.sendFailure(CommandFeedback.noPermission()); return 0;
                    }
                    return CommonCommandLogic.listReports(src);
                })
                .then(Commands.literal("unknown")
                    .executes(c -> {
                        CommandSourceStack src = c.getSource();
                        if (!CommandPermissionHelper.canViewServerReports(src)) {
                            src.sendFailure(CommandFeedback.noPermission()); return 0;
                        }
                        return CommonCommandLogic.listUnknownReports(src);
                    })))

            .then(Commands.literal("show")
                .then(Commands.argument("report_id", StringArgumentType.word())
                    .executes(c -> {
                        CommandSourceStack src = c.getSource();
                        if (!CommandPermissionHelper.canViewServerReports(src)) {
                            src.sendFailure(CommandFeedback.noPermission()); return 0;
                        }
                        return CommonCommandLogic.showReport(src, StringArgumentType.getString(c, "report_id"));
                    })))

            .then(Commands.literal("delete")
                .then(Commands.argument("report_id", StringArgumentType.word())
                    .executes(c -> CommonCommandLogic.deleteReport(
                        c.getSource(), StringArgumentType.getString(c, "report_id")))))

            .then(Commands.literal("submit")
                .executes(c -> CommonCommandLogic.submitLast(c.getSource()))
                .then(Commands.literal("last")
                    .executes(c -> CommonCommandLogic.submitLast(c.getSource())))
                .then(Commands.literal("prepare")
                    .then(Commands.literal("last")
                        .executes(c -> CommonCommandLogic.submitPrepareLast(c.getSource())))
                    .then(Commands.argument("report_id", StringArgumentType.word())
                        .executes(c -> CommonCommandLogic.submitReport(
                            c.getSource(), StringArgumentType.getString(c, "report_id")))))
                .then(Commands.literal("yes")
                    .executes(c -> CommonCommandLogic.submitYes(c.getSource())))
                .then(Commands.literal("send")
                    .executes(c -> CommonCommandLogic.submitSend(c.getSource())))
                .then(Commands.literal("confirm")
                    .executes(c -> CommonCommandLogic.submitConfirmLast(c.getSource()))
                    .then(Commands.literal("last")
                        .executes(c -> CommonCommandLogic.submitConfirmLast(c.getSource())))
                    .then(Commands.argument("prepared_id", StringArgumentType.greedyString())
                        .executes(c -> CommonCommandLogic.submitConfirm(
                            c.getSource(), StringArgumentType.getString(c, "prepared_id")))))
                .then(Commands.literal("cancel")
                    .then(Commands.argument("prepared_id", StringArgumentType.greedyString())
                        .executes(c -> CommonCommandLogic.submitCancelPrepared(
                            c.getSource(), StringArgumentType.getString(c, "prepared_id")))))
                .then(Commands.literal("local")
                    .then(Commands.literal("last")
                        .executes(c -> CommonCommandLogic.submitLocalLast(c.getSource()))))
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.submitStatus(c.getSource())))
                .then(Commands.literal("reset")
                    .executes(c -> CommonCommandLogic.submitReset(c.getSource())))
                .then(Commands.literal("health")
                    .executes(c -> CommonCommandLogic.submitHealth(c.getSource())))
                .then(Commands.literal("check")
                    .then(Commands.argument("report_id", StringArgumentType.greedyString())
                        .executes(c -> CommonCommandLogic.submitCheckStatus(
                            c.getSource(), StringArgumentType.getString(c, "report_id")))))
                .then(Commands.literal("debug-routing")
                    .executes(c -> CommonCommandLogic.submitDebugRouting(c.getSource())))
                .then(Commands.literal("mode")
                    .then(Commands.literal("cloudflare")
                        .executes(c -> CommonCommandLogic.submitModeCloudflare(c.getSource())))
                    .then(Commands.literal("local")
                        .executes(c -> CommonCommandLogic.submitModeLocal(c.getSource())))
                    .then(Commands.literal("status")
                        .executes(c -> CommonCommandLogic.submitModeStatus(c.getSource()))))
                .then(Commands.literal("crash")
                    .then(Commands.literal("last")
                        .executes(c -> CommonCommandLogic.submitCrashLast(c.getSource())))
                    .then(Commands.argument("crash_id", StringArgumentType.word())
                        .executes(c -> CommonCommandLogic.submitCrash(
                            c.getSource(), StringArgumentType.getString(c, "crash_id")))))
                .then(Commands.literal("guard")
                    .then(Commands.literal("last")
                        .executes(c -> CommonCommandLogic.submitGuardLast(c.getSource())))
                    .then(Commands.argument("guard_id", StringArgumentType.word())
                        .executes(c -> CommonCommandLogic.submitGuard(
                            c.getSource(), StringArgumentType.getString(c, "guard_id")))))
                .then(Commands.literal("preview")
                    .executes(c -> CommonCommandLogic.submitPreview(c.getSource()))
                    .then(Commands.literal("last")
                        .executes(c -> CommonCommandLogic.submitPreview(c.getSource()))))
                .then(Commands.argument("report_id", StringArgumentType.word())
                    .executes(c -> CommonCommandLogic.submitReport(
                        c.getSource(), StringArgumentType.getString(c, "report_id")))))

            .then(Commands.literal("config")
                .then(Commands.literal("reload")
                    .executes(c -> CommonCommandLogic.reloadConfig(c.getSource()))))

            .then(Commands.literal("crash")
                .then(Commands.literal("last")
                    .executes(c -> CommonCommandLogic.crashLast(c.getSource())))
                .then(Commands.literal("list")
                    .executes(c -> CommonCommandLogic.crashList(c.getSource())))
                .then(Commands.literal("show")
                    .then(Commands.argument("crash_id", StringArgumentType.word())
                        .executes(c -> CommonCommandLogic.crashShow(
                            c.getSource(), StringArgumentType.getString(c, "crash_id")))))
                .then(Commands.literal("export")
                    .then(Commands.argument("crash_id", StringArgumentType.word())
                        .executes(c -> CommonCommandLogic.crashExport(
                            c.getSource(), StringArgumentType.getString(c, "crash_id"))))))

            .then(Commands.literal("guard")
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.guardStatus(c.getSource())))
                .then(Commands.literal("list")
                    .executes(c -> CommonCommandLogic.guardList(c.getSource())))
                .then(Commands.literal("info")
                    .then(Commands.argument("guard_id", StringArgumentType.word())
                        .executes(c -> CommonCommandLogic.guardInfo(
                            c.getSource(), StringArgumentType.getString(c, "guard_id")))))
                .then(Commands.literal("enable")
                    .then(Commands.argument("guard_id", StringArgumentType.word())
                        .executes(c -> CommonCommandLogic.guardEnable(
                            c.getSource(), StringArgumentType.getString(c, "guard_id")))))
                .then(Commands.literal("disable")
                    .then(Commands.argument("guard_id", StringArgumentType.word())
                        .executes(c -> CommonCommandLogic.guardDisable(
                            c.getSource(), StringArgumentType.getString(c, "guard_id")))))
                .then(Commands.literal("report")
                    .then(Commands.literal("last")
                        .executes(c -> CommonCommandLogic.guardReportLast(c.getSource())))))

            .then(Commands.literal("debug")
                .then(Commands.literal("on")
                    .executes(c -> CommonCommandLogic.enableDebug(c.getSource())))
                .then(Commands.literal("off")
                    .executes(c -> CommonCommandLogic.disableDebug(c.getSource())))
                .then(Commands.literal("freeze")
                    .then(Commands.literal("server")
                        .then(Commands.argument("milliseconds", IntegerArgumentType.integer(1, 30000))
                            .executes(c -> {
                                CommandSourceStack src = c.getSource();
                                if (!CommandPermissionHelper.canUseDebug(src)) {
                                    src.sendFailure(CommandFeedback.debugDisabled()); return 0;
                                }
                                int ms = IntegerArgumentType.getInteger(c, "milliseconds");
                                src.sendSuccess(() -> CommandFeedback.debug("Freezing server thread for " + ms + " ms..."), true);
                                try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
                                src.sendSuccess(() -> CommandFeedback.debug("Server thread unfreeze complete."), true);
                                return 1;
                            })))
                    .then(Commands.literal("client")
                        .executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })))
                .then(Commands.literal("gc")
                    .executes(c -> {
                        CommandSourceStack src = c.getSource();
                        if (!CommandPermissionHelper.canUseDebug(src)) {
                            src.sendFailure(CommandFeedback.debugDisabled()); return 0;
                        }
                        System.gc();
                        src.sendSuccess(() -> CommandFeedback.debug("GC hint sent."), true);
                        return 1;
                    }))
                .then(Commands.literal("generate-test-report")
                    .executes(c -> CommonCommandLogic.generateTestReport(c.getSource())))
                .then(Commands.literal("test")
                    .then(Commands.literal("minor")
                        .executes(c -> CommonCommandLogic.debugTestMinor(c.getSource())))
                    .then(Commands.literal("medium")
                        .executes(c -> CommonCommandLogic.debugTestMedium(c.getSource())))
                    .then(Commands.literal("severe")
                        .executes(c -> CommonCommandLogic.debugTestSevere(c.getSource())))
                    .then(Commands.literal("extreme")
                        .executes(c -> CommonCommandLogic.debugTestExtreme(c.getSource()))))
                .then(Commands.literal("visibility-test")
                    .executes(c -> CommonCommandLogic.debugVisibilityTest(c.getSource())))
                .then(Commands.literal("command-routing")
                    .executes(c -> CommonCommandLogic.debugCommandRouting(c.getSource()))))

            .then(Commands.literal("verbose")
                .executes(c -> CommonCommandLogic.verboseStatus(c.getSource()))
                .then(Commands.literal("on")
                    .executes(c -> CommonCommandLogic.verboseOn(c.getSource())))
                .then(Commands.literal("off")
                    .executes(c -> CommonCommandLogic.verboseOff(c.getSource())))
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.verboseStatus(c.getSource()))))
            .then(Commands.literal("notifications")
                .then(Commands.literal("minor")
                    .then(Commands.literal("on")
                        .executes(c -> CommonCommandLogic.verboseOn(c.getSource())))
                    .then(Commands.literal("off")
                        .executes(c -> CommonCommandLogic.verboseOff(c.getSource()))))
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.verboseStatus(c.getSource()))))

            .then(Commands.literal("server")
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.showStatus(c.getSource())))
                .then(Commands.literal("health")
                    .executes(c -> CommonCommandLogic.showHealth(c.getSource())))
                .then(Commands.literal("last")
                    .executes(c -> CommonCommandLogic.showLast(c.getSource())))
                .then(Commands.literal("report")
                    .executes(c -> {
                        CommandSourceStack src = c.getSource();
                        if (!CommandPermissionHelper.canViewServerReports(src)) {
                            src.sendFailure(CommandFeedback.noPermission()); return 0;
                        }
                        return CommonCommandLogic.generateReport(src);
                    }))
                .then(Commands.literal("submit")
                    .then(Commands.literal("last")
                        .executes(c -> CommonCommandLogic.submitLast(c.getSource())))))

            .then(Commands.literal("f3")
                .executes(c -> CommonCommandLogic.f3Status(c.getSource()))
                .then(Commands.literal("on")
                    .executes(c -> CommonCommandLogic.f3On(c.getSource())))
                .then(Commands.literal("off")
                    .executes(c -> CommonCommandLogic.f3Off(c.getSource())))
                .then(Commands.literal("toggle")
                    .executes(c -> CommonCommandLogic.f3Toggle(c.getSource())))
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.f3Status(c.getSource()))))

            .then(Commands.literal("overlay")
                .executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })
                .then(Commands.literal("on").executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("off").executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })))

            .then(Commands.literal("client")
                .executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })
                .then(Commands.literal("last").executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("report").executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })))

            .then(Commands.literal("alerts")
                .executes(c -> CommonCommandLogic.alertsStatus(c.getSource()))
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.alertsStatus(c.getSource())))
                .then(Commands.literal("minor")
                    .executes(c -> CommonCommandLogic.alertsSetMode(c.getSource(), com.stutteranalyzer.core.AlertMode.MINOR)))
                .then(Commands.literal("medium")
                    .executes(c -> CommonCommandLogic.alertsSetMode(c.getSource(), com.stutteranalyzer.core.AlertMode.MEDIUM)))
                .then(Commands.literal("severe")
                    .executes(c -> CommonCommandLogic.alertsSetMode(c.getSource(), com.stutteranalyzer.core.AlertMode.SEVERE)))
                .then(Commands.literal("extreme")
                    .executes(c -> CommonCommandLogic.alertsSetMode(c.getSource(), com.stutteranalyzer.core.AlertMode.EXTREME)))
                .then(Commands.literal("off")
                    .executes(c -> CommonCommandLogic.alertsSetMode(c.getSource(), com.stutteranalyzer.core.AlertMode.OFF)))
                .then(Commands.literal("cooldown")
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                        .executes(c -> CommonCommandLogic.alertsCooldown(
                            c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))))
                .then(Commands.literal("test")
                    .executes(c -> CommonCommandLogic.alertsTest(c.getSource()))))
        );
    }
}
