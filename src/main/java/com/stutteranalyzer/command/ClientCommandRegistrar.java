package com.stutteranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.stutteranalyzer.client.DebugHudStatusProvider;
import com.stutteranalyzer.client.F3StatusFormatter;
import com.stutteranalyzer.config.SAConfig;
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
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "MINOR"))))
                .then(Commands.literal("medium")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "MEDIUM"))))
                .then(Commands.literal("severe")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "SEVERE"))))
                .then(Commands.literal("extreme")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "EXTREME"))))
                .then(Commands.literal("off")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.alertsSetMode(ctx.getSource(), "OFF"))))
                .then(Commands.literal("test")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.alertsTest(ctx.getSource()))))
                .then(Commands.literal("cooldown")
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                        .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.alertsCooldown(
                            ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))))

            // ── preview (alias for submit preview) ───────────────────────────
            .then(Commands.literal("preview")
                .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitPreview(ctx.getSource()))))

            // ── submit ────────────────────────────────────────────────────────
            .then(Commands.literal("submit")
                .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitLast(ctx.getSource())))
                .then(Commands.literal("preview")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitPreview(ctx.getSource()))))
                .then(Commands.literal("status")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitStatus(ctx.getSource()))))
                .then(Commands.literal("health")
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.submitHealth(ctx.getSource())))))

            // ── reports ───────────────────────────────────────────────────────
            .then(Commands.literal("reports")
                .executes(ctx -> CommonCommandLogic.listReports(ctx.getSource())))
            .then(Commands.literal("last")
                .executes(ctx -> CommonCommandLogic.showLast(ctx.getSource())))

            // ── f3 (client-side, actual implementation) ───────────────────────
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

            // ── overlay (client-side) ─────────────────────────────────────────
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
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.overlay.status_off")), false);
                        return 1;
                    })))

            // ── show <time> ───────────────────────────────────────────────────
            .then(Commands.literal("show")
                .then(Commands.argument("time", StringArgumentType.word())
                    .executes(ctx -> safe(ctx.getSource(), () -> CommonCommandLogic.showRecentEvents(
                        ctx.getSource(), StringArgumentType.getString(ctx, "time"))))))

            // ── optimize ──────────────────────────────────────────────────────
            .then(Commands.literal("optimize")
                .then(Commands.literal("suggest")
                    .executes(ctx -> CommonCommandLogic.optimizeSuggest(ctx.getSource())))
                .then(Commands.literal("install")
                    .executes(ctx -> CommonCommandLogic.optimizeInstall(ctx.getSource()))))
        );
    }
}
