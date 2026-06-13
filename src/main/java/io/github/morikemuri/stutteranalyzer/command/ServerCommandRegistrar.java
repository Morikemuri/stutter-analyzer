package io.github.morikemuri.stutteranalyzer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.morikemuri.stutteranalyzer.config.SAConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

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

            // â”€â”€ bare /sa â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            .executes(ctx -> CommonCommandLogic.quickDashboard(ctx.getSource()))

            // â”€â”€ main â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            .then(Commands.literal("help")
                .executes(ctx -> CommonCommandLogic.showHelp(ctx.getSource())))
            .then(Commands.literal("status")
                .executes(ctx -> CommonCommandLogic.showStatus(ctx.getSource())))
            .then(Commands.literal("version")
                .executes(ctx -> CommonCommandLogic.showVersion(ctx.getSource())))
            .then(Commands.literal("privacy")
                .executes(ctx -> CommonCommandLogic.showPrivacy(ctx.getSource())))

            // â”€â”€ alerts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

            // â”€â”€ preview (alias for submit preview) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            .then(Commands.literal("preview")
                .executes(ctx -> CommonCommandLogic.submitPreview(ctx.getSource())))

            // â”€â”€ submit â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            .then(Commands.literal("submit")
                .executes(ctx -> CommonCommandLogic.submitLast(ctx.getSource()))
                .then(Commands.literal("preview")
                    .executes(ctx -> CommonCommandLogic.submitPreview(ctx.getSource())))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.submitStatus(ctx.getSource())))
                .then(Commands.literal("health")
                    .executes(ctx -> CommonCommandLogic.submitHealth(ctx.getSource()))))

            // â”€â”€ reports â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            .then(Commands.literal("reports")
                .executes(ctx -> CommonCommandLogic.listReports(ctx.getSource())))
            .then(Commands.literal("last")
                .executes(ctx -> CommonCommandLogic.showLast(ctx.getSource())))

            // â”€â”€ f3 (client-only, fails gracefully on server) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            .then(Commands.literal("f3")
                .executes(ctx -> CommonCommandLogic.f3Status(ctx.getSource()))
                .then(Commands.literal("on")
                    .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("off")
                    .executes(ctx -> { ctx.getSource().sendFailure(CommandFeedback.clientOnly()); return 0; }))
                .then(Commands.literal("status")
                    .executes(ctx -> CommonCommandLogic.f3Status(ctx.getSource()))))

            // â”€â”€ overlay (server: dedicated server message) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            .then(Commands.literal("overlay")
                .executes(ctx -> { ctx.getSource().sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.overlay.server_only")), false); return 1; })
                .then(Commands.literal("on")    .executes(ctx -> { ctx.getSource().sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.overlay.server_only")), false); return 1; }))
                .then(Commands.literal("off")   .executes(ctx -> { ctx.getSource().sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.overlay.server_only")), false); return 1; }))
                .then(Commands.literal("status").executes(ctx -> { ctx.getSource().sendSuccess(() -> CommandFeedback.warn(Component.translatable("stutteranalyzer.overlay.server_only")), false); return 1; })))

            // â”€â”€ show <time> â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            .then(Commands.literal("show")
                .executes(ctx -> CommonCommandLogic.showRecentEvents(ctx.getSource(), "15m"))
                .then(Commands.argument("time", StringArgumentType.word())
                    .executes(ctx -> CommonCommandLogic.showRecentEvents(
                        ctx.getSource(), StringArgumentType.getString(ctx, "time")))))

            // â”€â”€ optimize â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            .then(Commands.literal("optimize")
                .executes(ctx -> CommonCommandLogic.optimizeSuggest(ctx.getSource()))
                .then(Commands.literal("suggest")
                    .executes(ctx -> CommonCommandLogic.optimizeSuggest(ctx.getSource())))
                .then(Commands.literal("install")
                    .executes(ctx -> CommonCommandLogic.optimizeInstall(ctx.getSource()))))
        );
    }
}
