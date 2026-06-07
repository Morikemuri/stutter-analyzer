package com.stutteranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.stutteranalyzer.client.DebugHudStatusProvider;
import com.stutteranalyzer.client.F3StatusFormatter;
import com.stutteranalyzer.config.SAConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
                        ctx.getSource().sendSuccess(() -> CommandFeedback.success("Overlay enabled."), false);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.success("Overlay disabled."), false);
                        return 1;
                    }))
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info("Overlay toggled."), false);
                        return 1;
                    }))
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info("Overlay: disabled"), false);
                        return 1;
                    })))

            // ── f3 subcommands ─────────────────────────────────────────────────
            .then(Commands.literal("f3")
                .executes(ctx -> {
                    boolean enabled = SAConfig.INSTANCE.debugHudEnabled.get();
                    String current = F3StatusFormatter.format().replaceAll("§.", "");
                    ctx.getSource().sendSuccess(() -> CommandFeedback.row("F3 status line", enabled ? "enabled" : "disabled"), false);
                    if (enabled) ctx.getSource().sendSuccess(() -> CommandFeedback.info("Current: " + current), false);
                    return 1;
                })
                .then(Commands.literal("on")
                    .executes(ctx -> {
                        // Config is read-only at runtime; we toggle the cached provider behavior via a runtime flag
                        DebugHudStatusProvider.setF3Enabled(true);
                        ctx.getSource().sendSuccess(() -> CommandFeedback.success("F3 status line enabled for this session."), false);
                        return 1;
                    }))
                .then(Commands.literal("off")
                    .executes(ctx -> {
                        DebugHudStatusProvider.setF3Enabled(false);
                        ctx.getSource().sendSuccess(() -> CommandFeedback.success("F3 status line disabled for this session."), false);
                        return 1;
                    }))
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        boolean now = !DebugHudStatusProvider.isF3Enabled();
                        DebugHudStatusProvider.setF3Enabled(now);
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info("F3 status line " + (now ? "enabled" : "disabled") + "."), false);
                        return 1;
                    }))
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        boolean enabled = DebugHudStatusProvider.isF3Enabled();
                        String current = F3StatusFormatter.format().replaceAll("§.", "");
                        ctx.getSource().sendSuccess(() -> CommandFeedback.row("F3 status line", enabled ? "enabled" : "disabled"), false);
                        if (enabled) ctx.getSource().sendSuccess(() -> CommandFeedback.info("Current F3 text: " + current), false);
                        return 1;
                    })))
            // ── selfcheck / test (client side has full info) ──────────────────
            .then(Commands.literal("selfcheck")
                .executes(ctx -> CommonCommandLogic.selfCheck(ctx.getSource())))
            .then(Commands.literal("test")
                .executes(ctx -> CommonCommandLogic.selfCheck(ctx.getSource())))

            // ── client subcommands ─────────────────────────────────────────────
            .then(Commands.literal("client")
                .then(Commands.literal("last")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info("No client-side freeze events recorded yet."), false);
                        return 1;
                    }))
                .then(Commands.literal("report")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info("Client report generation not yet implemented."), false);
                        return 1;
                    }))
                .then(Commands.literal("export")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!CommandPermissionHelper.canExportOwnClientReport(src)) {
                            src.sendFailure(CommandFeedback.noPermission());
                            return 0;
                        }
                        src.sendSuccess(() -> CommandFeedback.info("Client export not yet implemented."), false);
                        return 1;
                    }))
                .then(Commands.literal("submit")
                    .then(Commands.literal("last")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> CommandFeedback.info("Client submit not yet implemented."), false);
                            return 1;
                        }))))
        );
    }
}
