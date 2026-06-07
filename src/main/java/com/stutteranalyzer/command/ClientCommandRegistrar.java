package com.stutteranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.stutteranalyzer.classifier.FreezeDetector;
import com.stutteranalyzer.client.DebugHudStatusProvider;
import com.stutteranalyzer.client.F3StatusFormatter;
import com.stutteranalyzer.config.SAConfig;
import com.stutteranalyzer.core.MetricsCollector;
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

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root)
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
                        .executes(ctx -> CommonCommandLogic.submitLast(ctx.getSource())))))

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
                        .executes(ctx -> CommonCommandLogic.debugTestSevere(ctx.getSource())))))
        );
    }
}
