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

            // ── bare /sa ──────────────────────────────────────────────────────
            .executes(ctx -> CommonCommandLogic.quickDashboard(ctx.getSource()))

            // ── main ──────────────────────────────────────────────────────────
            .then(Commands.literal("help")
                .executes(ctx -> CommonCommandLogic.showHelp(ctx.getSource())))
            .then(Commands.literal("status")
                .executes(ctx -> CommonCommandLogic.showStatus(ctx.getSource())))
            .then(Commands.literal("version")
                .executes(ctx -> CommonCommandLogic.showVersion(ctx.getSource())))
            .then(Commands.literal("privacy")
                .executes(ctx -> CommonCommandLogic.showPrivacy(ctx.getSource())))

            // ── alerts ────────────────────────────────────────────────────────
            .then(Commands.literal("alerts")
                .executes(ctx -> CommonCommandLogic.alertsStatus(ctx.getSource()))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.alertsStatus(ctx.getSource())))
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
                .then(Commands.literal("test")
                    .executes(ctx -> CommonCommandLogic.alertsTest(ctx.getSource())))
                .then(Commands.literal("cooldown")
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                        .executes(ctx -> CommonCommandLogic.alertsCooldown(
                            ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"))))))

            // ── preview (alias for submit preview) ───────────────────────────
            .then(Commands.literal("preview")
                .executes(ctx -> CommonCommandLogic.submitPreview(ctx.getSource())))

            // ── submit ────────────────────────────────────────────────────────
            .then(Commands.literal("submit")
                .executes(ctx -> CommonCommandLogic.submitLast(ctx.getSource()))
                .then(Commands.literal("preview")
                    .executes(ctx -> CommonCommandLogic.submitPreview(ctx.getSource())))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.submitStatus(ctx.getSource())))
                .then(Commands.literal("health")
                    .executes(ctx -> CommonCommandLogic.submitHealth(ctx.getSource()))))

            // ── reports ───────────────────────────────────────────────────────
            .then(Commands.literal("reports")
                .executes(ctx -> CommonCommandLogic.listReports(ctx.getSource())))
            .then(Commands.literal("last")
                .executes(ctx -> CommonCommandLogic.showLast(ctx.getSource())))
            .then(Commands.literal("delete")
                .then(Commands.argument("report_id", StringArgumentType.word())
                    .executes(ctx -> CommonCommandLogic.deleteReport(
                        ctx.getSource(), StringArgumentType.getString(ctx, "report_id")))))

            // ── f3 (client-only, fails gracefully on server) ──────────────────
            .then(Commands.literal("f3")
                .executes(ctx -> CommonCommandLogic.f3Status(ctx.getSource()))
                .then(Commands.literal("on")
                    .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("off")
                    .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.f3Status(ctx.getSource()))))

            // ── overlay (client-only, fails gracefully on server) ─────────────
            .then(Commands.literal("overlay")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })
                .then(Commands.literal("on")    .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("off")   .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("status").executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; })))

            // ── optimize ──────────────────────────────────────────────────────
            .then(Commands.literal("optimize")
                .then(Commands.literal("suggest")
                    .executes(ctx -> CommonCommandLogic.optimizeSuggest(ctx.getSource()))))
        );
    }
}
