package com.stutteranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class FabricCommandRegistrar {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root)

            // ── bare /sa ──────────────────────────────────────────────────────
            .executes(c -> CommonCommandLogic.quickDashboard(c.getSource()))

            // ── main ──────────────────────────────────────────────────────────
            .then(Commands.literal("help")
                .executes(c -> CommonCommandLogic.showHelp(c.getSource())))
            .then(Commands.literal("status")
                .executes(c -> CommonCommandLogic.showStatus(c.getSource())))
            .then(Commands.literal("version")
                .executes(c -> CommonCommandLogic.showVersion(c.getSource())))
            .then(Commands.literal("privacy")
                .executes(c -> CommonCommandLogic.showPrivacy(c.getSource())))

            // ── alerts ────────────────────────────────────────────────────────
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

            // ── submit ────────────────────────────────────────────────────────
            .then(Commands.literal("submit")
                .executes(c -> CommonCommandLogic.submitLast(c.getSource()))
                .then(Commands.literal("preview")
                    .executes(c -> CommonCommandLogic.submitPreview(c.getSource())))
                .then(Commands.literal("check")
                    .then(Commands.argument("report_id", StringArgumentType.greedyString())
                        .executes(c -> CommonCommandLogic.submitCheckStatus(
                            c.getSource(), StringArgumentType.getString(c, "report_id")))))
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.submitStatus(c.getSource())))
                .then(Commands.literal("health")
                    .executes(c -> CommonCommandLogic.submitHealth(c.getSource()))))

            // ── reports ───────────────────────────────────────────────────────
            .then(Commands.literal("reports")
                .executes(c -> CommonCommandLogic.listReports(c.getSource())))
            .then(Commands.literal("last")
                .executes(c -> CommonCommandLogic.showLast(c.getSource())))
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

            // ── f3 (client-only) ──────────────────────────────────────────────
            .then(Commands.literal("f3")
                .executes(c -> CommonCommandLogic.f3Status(c.getSource()))
                .then(Commands.literal("on")
                    .executes(c -> CommonCommandLogic.f3On(c.getSource())))
                .then(Commands.literal("off")
                    .executes(c -> CommonCommandLogic.f3Off(c.getSource())))
                .then(Commands.literal("status")
                    .executes(c -> CommonCommandLogic.f3Status(c.getSource()))))

            // ── overlay (client-only) ─────────────────────────────────────────
            .then(Commands.literal("overlay")
                .executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })
                .then(Commands.literal("on")    .executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("off")   .executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("status").executes(c -> { c.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })))

            // ── explain / optimize ────────────────────────────────────────────
            .then(Commands.literal("explain")
                .then(Commands.argument("category", StringArgumentType.word())
                    .executes(c -> CommonCommandLogic.explainCategory(
                        c.getSource(), StringArgumentType.getString(c, "category")))))
            .then(Commands.literal("optimize")
                .then(Commands.literal("suggest")
                    .executes(c -> CommonCommandLogic.optimizeSuggest(c.getSource()))))
        );
    }
}
