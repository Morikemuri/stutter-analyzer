package com.stutteranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.stutteranalyzer.client.DebugHudStatusProvider;
import com.stutteranalyzer.client.F3StatusFormatter;
import com.stutteranalyzer.config.SAConfig;
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
                .then(Commands.literal("last")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.client.no_data")), false);
                        return 1;
                    }))
                .then(Commands.literal("report")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.client.report_not_impl")), false);
                        return 1;
                    }))
                .then(Commands.literal("export")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!CommandPermissionHelper.canExportOwnClientReport(src)) {
                            src.sendFailure(CommandFeedback.noPermission());
                            return 0;
                        }
                        src.sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.client.export_not_impl")), false);
                        return 1;
                    }))
                .then(Commands.literal("submit")
                    .then(Commands.literal("last")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> CommandFeedback.info(Component.translatable("stutteranalyzer.cmd.client.submit_not_impl")), false);
                            return 1;
                        }))))
        );
    }
}
